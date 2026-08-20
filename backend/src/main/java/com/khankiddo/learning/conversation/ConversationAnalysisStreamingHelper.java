package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.apache.commons.lang3.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 2 主分析：LangChain4j 调用语法 JSON。
 * <p>
 * 单批（句数 ≤ batch-threshold）走 {@link StreamingChatModel}，仅供前端逐句预览；
 * 分批走 {@link ChatModel} 非流式，避免并发打满流式连接。预览不是正确性前提。
 */
@Slf4j
@Component
public class ConversationAnalysisStreamingHelper {

    private static final int STREAMING_PREVIEW_MAX = 80;
    private static final int GRAMMAR_PARSE_MAX_ATTEMPTS = 2;

    private final LlmChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;
    private final Duration streamWallClockTimeout;

    public ConversationAnalysisStreamingHelper(
            LlmChatModelFactory chatModelFactory,
            ObjectMapper objectMapper,
            ConversationAnalysisProperties properties) {
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
        this.streamWallClockTimeout = properties.getStreamWallClockTimeout();
    }

    public GrammarAnalysisResult streamGrammarAnalysis(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            Consumer<ConversationAnalysisProgress> onProgress) {
        return analyzeGrammarJson(systemPrompt, userPrompt, model, 0, 0, true, onProgress);
    }

    public GrammarAnalysisResult streamGrammarAnalysis(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            int batchNum,
            int totalBatches,
            Consumer<ConversationAnalysisProgress> onProgress) {
        return analyzeGrammarJson(systemPrompt, userPrompt, model, batchNum, totalBatches, true, onProgress);
    }

    /**
     * Stage 2 非流式语法分析。分批并发时使用，避免同时打满流式连接。
     */
    public GrammarAnalysisResult analyzeGrammarWithoutStreaming(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            int batchNum,
            int totalBatches,
            Consumer<ConversationAnalysisProgress> onProgress) {
        return analyzeGrammarJson(systemPrompt, userPrompt, model, batchNum, totalBatches, false, onProgress);
    }

    private GrammarAnalysisResult analyzeGrammarJson(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            int batchNum,
            int totalBatches,
            boolean streaming,
            Consumer<ConversationAnalysisProgress> onProgress) {

        boolean batched = totalBatches > 1;
        Consumer<ConversationAnalysisProgress> progressSink = batched
                ? progress -> onProgress.accept(withBatchPrefix(progress, batchNum, totalBatches))
                : onProgress;

        String startMessage = batched
                ? String.format("正在分析第 %d 批（共 %d 批）...", batchNum, totalBatches)
                : "正在分析用户英文表达...";

        ConversationAnalysisProgress.ConversationAnalysisProgressBuilder startBuilder =
                ConversationAnalysisProgress.builder()
                        .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                        .message(startMessage);
        if (streaming) {
            startBuilder.streamingOriginal("...");
        }
        progressSink.accept(startBuilder.build());

        GrammarAnalysisResult result = null;
        AtomicInteger nextAttempt = new AtomicInteger(1);
        for (int parseAttempt = 1; parseAttempt <= GRAMMAR_PARSE_MAX_ATTEMPTS; parseAttempt++) {
            StreamedGrammarJson streamed;
            if (streaming && parseAttempt == 1) {
                try {
                    streamed = streamJsonText(
                            systemPrompt, userPrompt, model, progressSink, nextAttempt, batchNum, totalBatches);
                    if (!streamed.streamCompletedNormally()) {
                        log.warn("语法分析流式响应未正常结束 (finishReason={}, tokenUsage={}, batchNum={}, totalBatches:{})，改用非流式请求",
                                streamed.finishReason(), streamed.tokenUsage(), batchNum, totalBatches);
                        streamed = fetchGrammarJsonSync(
                                systemPrompt, userPrompt, model, nextAttempt, batchNum, totalBatches);
                    }
                } catch (BadRequestException ex) {
                    if (!ConversationAnalysisIoRetryPolicy.isRetryable(ex)) {
                        throw ex;
                    }
                    log.warn("语法分析流式 I/O 失败，改用非流式 (batchNum={}, totalBatches={}, cause={})",
                            batchNum, totalBatches,
                            ex.getCause() != null ? ex.getCause().toString() : ex.getMessage());
                    streamed = fetchGrammarJsonSync(
                            systemPrompt, userPrompt, model, nextAttempt, batchNum, totalBatches);
                }
            } else {
                streamed = fetchGrammarJsonSync(
                        systemPrompt, userPrompt, model, nextAttempt, batchNum, totalBatches);
            }
            try {
                result = parseGrammarJson(streamed.text(), streamed.finishReason());
                break;
            } catch (BadRequestException ex) {
                if (parseAttempt >= GRAMMAR_PARSE_MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("语法分析 JSON 解析失败，准备重试 ({}/{}): finishReason={}, length={}",
                        parseAttempt, GRAMMAR_PARSE_MAX_ATTEMPTS, streamed.finishReason(), streamed.text().length());
            }
        }
        progressSink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message(batched ? "正在接收第 " + batchNum + " 批分析结果..." : "正在接收 AI 分析结果...")
                .streamingOriginal("")
                .streamingSuggestion("")
                .streamingErrorsHint("")
                .build());
        return result;
    }

    private static ConversationAnalysisProgress withBatchPrefix(
            ConversationAnalysisProgress progress,
            int batchNum,
            int totalBatches) {

        String tag = "[" + batchNum + "/" + totalBatches + "] ";
        ConversationAnalysisProgress.ConversationAnalysisProgressBuilder builder =
                ConversationAnalysisProgress.builder()
                        .status(progress.getStatus())
                        .message(prefixIfHasText(progress.getMessage(), tag))
                        .result(progress.getResult())
                        .errorMessage(progress.getErrorMessage())
                        .messageStats(progress.getMessageStats())
                        .streamingOriginal(prefixIfHasText(progress.getStreamingOriginal(), tag))
                        .streamingSuggestion(prefixIfHasText(progress.getStreamingSuggestion(), tag))
                        .streamingErrorsHint(prefixIfHasText(progress.getStreamingErrorsHint(), tag))
                        .streamingCommitOriginal(prefixIfHasText(progress.getStreamingCommitOriginal(), tag))
                        .streamingCommitSuggestion(prefixIfHasText(progress.getStreamingCommitSuggestion(), tag))
                        .streamingCommitErrorsHint(prefixIfHasText(progress.getStreamingCommitErrorsHint(), tag));
        return builder.build();
    }

    private static String prefixIfHasText(String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.startsWith(prefix)) {
            return value;
        }
        return prefix + value;
    }

    private StreamedGrammarJson loggedGrammarCall(
            ResolvedLlmModel model,
            int attempt,
            int batchNum,
            int totalBatches,
            String mode,
            java.util.function.Supplier<StreamedGrammarJson> call) {
        long startedAt = System.currentTimeMillis();
        try {
            StreamedGrammarJson streamed = call.get();
            String result = ConversationAnalysisCallLog.MODE_STREAM.equals(mode) && !streamed.streamCompletedNormally()
                    ? ConversationAnalysisCallLog.RESULT_INCOMPLETE
                    : ConversationAnalysisCallLog.RESULT_OK;
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_GRAMMAR,
                    model != null ? model.getId() : null,
                    attempt,
                    System.currentTimeMillis() - startedAt,
                    result,
                    batchNum,
                    totalBatches,
                    mode);
            return streamed;
        } catch (RuntimeException ex) {
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_GRAMMAR,
                    model != null ? model.getId() : null,
                    attempt,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.resultOf(ex),
                    batchNum,
                    totalBatches,
                    mode);
            throw ex;
        }
    }

    private StreamedGrammarJson streamJsonText(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            Consumer<ConversationAnalysisProgress> onProgress,
            AtomicInteger nextAttempt,
            int batchNum,
            int totalBatches) {

        return loggedGrammarCall(
                model,
                nextAttempt.getAndIncrement(),
                batchNum,
                totalBatches,
                ConversationAnalysisCallLog.MODE_STREAM,
                () -> doStreamJsonText(systemPrompt, userPrompt, model, onProgress));
    }

    private StreamedGrammarJson doStreamJsonText(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            Consumer<ConversationAnalysisProgress> onProgress) {

        StreamingChatModel streamingChatModel = chatModelFactory.streamingForGrammarAnalysis(model);
        StringBuilder accumulated = new StringBuilder();
        String[] last = {null, null, null};
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<String> completeTextRef = new AtomicReference<>();
        AtomicReference<FinishReason> finishReasonRef = new AtomicReference<>();
        AtomicReference<TokenUsage> tokenUsageRef = new AtomicReference<>();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .build();

        streamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (!StringUtils.hasText(partialResponse)) {
                    return;
                }
                accumulated.append(partialResponse);
                emitStreamingProgress(accumulated.toString(), last, onProgress);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                if (response != null) {
                    finishReasonRef.set(response.finishReason());
                    tokenUsageRef.set(response.tokenUsage());
                    if (response.aiMessage() != null && StringUtils.hasText(response.aiMessage().text())) {
                        completeTextRef.set(response.aiMessage().text());
                    }
                }
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(streamWallClockTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BadRequestException("AI 分析超时，请缩短对话内容或稍后重试");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("AI 分析被中断");
        }

        if (errorRef.get() != null) {
            Throwable error = errorRef.get();
            throw new BadRequestException("AI 分析失败，请稍后重试", error);
        }
        String finalText = resolveFinalStreamText(accumulated.toString(), completeTextRef.get());
        if (!StringUtils.hasText(finalText)) {
            throw new BadRequestException("AI 未返回分析结果");
        }
        return new StreamedGrammarJson(
                finalText,
                finishReasonRef.get(),
                tokenUsageRef.get(),
                isStreamCompletedNormally(finishReasonRef.get(), tokenUsageRef.get()));
    }

    private StreamedGrammarJson fetchGrammarJsonSync(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            AtomicInteger nextAttempt,
            int batchNum,
            int totalBatches) {

        return loggedGrammarCall(
                model,
                nextAttempt.getAndIncrement(),
                batchNum,
                totalBatches,
                ConversationAnalysisCallLog.MODE_CHAT,
                () -> doFetchGrammarJsonSync(systemPrompt, userPrompt, model));
    }

    private StreamedGrammarJson doFetchGrammarJsonSync(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model) {

        ChatModel chatModel = chatModelFactory.chatForGrammarAnalysis(model);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .build();
        ChatResponse response = chatModel.chat(chatRequest);
        if (response == null || response.aiMessage() == null || !StringUtils.hasText(response.aiMessage().text())) {
            throw new BadRequestException("AI 未返回分析结果");
        }
        FinishReason finishReason = response.finishReason();
        TokenUsage tokenUsage = response.tokenUsage();
        return new StreamedGrammarJson(
                response.aiMessage().text(),
                finishReason,
                tokenUsage,
                isStreamCompletedNormally(finishReason, tokenUsage));
    }

    private static boolean isStreamCompletedNormally(FinishReason finishReason, TokenUsage tokenUsage) {
        return finishReason != null && ObjectUtils.isNotEmpty(tokenUsage);
    }

    private static String resolveFinalStreamText(String accumulated, String completeText) {
        if (!StringUtils.hasText(accumulated)) {
            return StringUtils.hasText(completeText) ? completeText.trim() : "";
        }
        if (!StringUtils.hasText(completeText)) {
            return accumulated.trim();
        }
        String partial = accumulated.trim();
        String complete = completeText.trim();
        if (complete.length() >= partial.length()) {
            return complete;
        }
        return partial;
    }

    private void emitStreamingProgress(
            String accumulated,
            String[] last,
            Consumer<ConversationAnalysisProgress> onProgress) {

        StreamingPreview preview = extractStreamingPreview(accumulated);
        boolean isNewItem = isNewStreamingItem(last[0], preview.original);

        // 流式 token 只更新预览字段，不带进度文案；否则分批模式下 Relay 剥掉预览后，
        // 同一句「正在接收…」会在并发批次交错下反复进入进度日志。
        ConversationAnalysisProgress.ConversationAnalysisProgressBuilder builder =
                ConversationAnalysisProgress.builder()
                        .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                        .streamingOriginal(preview.original);

        if (StringUtils.hasText(preview.suggestion)) {
            builder.streamingSuggestion(preview.suggestion);
        }
        if (StringUtils.hasText(preview.errorsHint)) {
            builder.streamingErrorsHint(preview.errorsHint);
        }
        if (isNewItem && last[0] != null && !"...".equals(last[0])) {
            builder.streamingCommitOriginal(last[0])
                    .streamingCommitSuggestion(last[1])
                    .streamingCommitErrorsHint(last[2]);
        }

        last[0] = preview.original;
        last[1] = preview.suggestion;
        last[2] = preview.errorsHint;
        onProgress.accept(builder.build());
    }

    GrammarAnalysisResult parseGrammarJson(String raw) {
        return parseGrammarJson(raw, null);
    }

    GrammarAnalysisResult parseGrammarJson(String raw, FinishReason finishReason) {
        try {
            String cleaned = stripMarkdownFence(raw);
            return objectMapper.readValue(cleaned, GrammarAnalysisResult.class);
        } catch (Exception ex) {
            log.warn("语法分析 JSON 解析失败: finishReason={}, length={}, error={}",
                    finishReason, StringUtils.hasText(raw) ? raw.length() : 0, ex.getMessage());
            if (finishReason == FinishReason.LENGTH) {
                throw new BadRequestException("AI 分析结果被截断，请减少单次分析句数或稍后重试");
            }
            throw new BadRequestException("AI 分析结果格式无效，请重试");
        }
    }

    private static String stripMarkdownFence(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) {
                trimmed = trimmed.substring(firstLineEnd + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private static boolean isNewStreamingItem(String lastOriginal, String currentOriginal) {
        if (!StringUtils.hasText(currentOriginal)
                || "...".equals(currentOriginal)
                || !StringUtils.hasText(lastOriginal)
                || "...".equals(lastOriginal)) {
            return false;
        }
        String last = lastOriginal.endsWith("...")
                ? lastOriginal.substring(0, lastOriginal.length() - 3)
                : lastOriginal;
        String current = currentOriginal.endsWith("...")
                ? currentOriginal.substring(0, currentOriginal.length() - 3)
                : currentOriginal;
        if (current.equals(last)) {
            return false;
        }
        return !current.startsWith(last) && !last.startsWith(current);
    }

    private static StreamingPreview extractStreamingPreview(String accumulated) {
        StreamingPreview preview = new StreamingPreview();
        preview.original = extractLastJsonString(accumulated, "originalSentence");
        preview.suggestion = extractLastJsonString(accumulated, "suggestion");
        int typeCount = 0;
        Matcher matcher = Pattern.compile("\"type\"\\s*:\\s*\"").matcher(accumulated);
        while (matcher.find()) {
            typeCount++;
        }
        preview.errorsHint = typeCount > 0 ? typeCount + " 个错误" : null;
        if (!StringUtils.hasText(preview.original)) {
            preview.original = "...";
        }
        return preview;
    }

    private static String extractLastJsonString(String text, String key) {
        Pattern full = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*?)\"");
        Matcher fullMatcher = full.matcher(text);
        String last = null;
        while (fullMatcher.find()) {
            last = fullMatcher.group(1);
        }
        if (last != null) {
            last = unescapeJsonString(last);
            return last.length() > STREAMING_PREVIEW_MAX ? last.substring(0, STREAMING_PREVIEW_MAX) + "..." : last;
        }
        Pattern partial = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)$");
        Matcher partialMatcher = partial.matcher(text);
        if (partialMatcher.find()) {
            last = unescapeJsonString(partialMatcher.group(1)) + "...";
            return last.length() > STREAMING_PREVIEW_MAX ? last.substring(0, STREAMING_PREVIEW_MAX) + "..." : last;
        }
        return null;
    }

    private static String unescapeJsonString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static final class StreamingPreview {
        private String original;
        private String suggestion;
        private String errorsHint;
    }

    private record StreamedGrammarJson(
            String text,
            FinishReason finishReason,
            TokenUsage tokenUsage,
            boolean streamCompletedNormally) {
    }
}
