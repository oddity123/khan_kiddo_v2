package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.exception.BadRequestException;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 2 主分析：LangChain4j {@link StreamingChatModel} 流式接收 JSON，
 * 按 delta 推送 {@link ConversationAnalysisProgress} 预览字段（与 v1 前端协议一致）。
 */
@Slf4j
@Component
public class ConversationAnalysisStreamingHelper {

    private static final int STREAMING_PREVIEW_MAX = 80;

    private final StreamingChatModel streamingChatModel;
    private final ObjectMapper objectMapper;

    @Value("${langchain4j.open-ai.streaming-chat-model.timeout:120s}")
    private Duration streamTimeout;

    public ConversationAnalysisStreamingHelper(
            @Qualifier("openAiStreamingChatModel") StreamingChatModel streamingChatModel,
            ObjectMapper objectMapper) {
        this.streamingChatModel = streamingChatModel;
        this.objectMapper = objectMapper;
    }

    public GrammarAnalysisResult streamGrammarAnalysis(
            String systemPrompt,
            String userPrompt,
            Consumer<ConversationAnalysisProgress> onProgress) {
        return streamGrammarAnalysis(systemPrompt, userPrompt, 0, 0, onProgress);
    }

    public GrammarAnalysisResult streamGrammarAnalysis(
            String systemPrompt,
            String userPrompt,
            int batchNum,
            int totalBatches,
            Consumer<ConversationAnalysisProgress> onProgress) {

        boolean batched = totalBatches > 1;
        Consumer<ConversationAnalysisProgress> progressSink = batched
                ? progress -> onProgress.accept(withBatchPrefix(progress, batchNum, totalBatches))
                : onProgress;

        String startMessage = batched
                ? String.format("正在分析第 %d 批（共 %d 批）...", batchNum, totalBatches)
                : "正在分析用户英文表达...";

        progressSink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message(startMessage)
                .streamingOriginal("...")
                .build());

        String jsonText = streamJsonText(systemPrompt, userPrompt, progressSink);
        progressSink.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message(batched ? "正在接收第 " + batchNum + " 批分析结果..." : "正在接收 AI 分析结果...")
                .streamingOriginal("")
                .streamingSuggestion("")
                .streamingErrorsHint("")
                .build());
        return parseGrammarJson(jsonText);
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

    private String streamJsonText(
            String systemPrompt,
            String userPrompt,
            Consumer<ConversationAnalysisProgress> onProgress) {

        StringBuilder accumulated = new StringBuilder();
        String[] last = {null, null, null};
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .build();

        streamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                accumulated.append(partialResponse);
                emitStreamingProgress(accumulated.toString(), last, onProgress);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(streamTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BadRequestException("AI 分析超时，请缩短对话内容或稍后重试");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("AI 分析被中断");
        }

        if (errorRef.get() != null) {
            throw new BadRequestException("AI 分析失败: " + errorRef.get().getMessage());
        }
        if (accumulated.isEmpty()) {
            throw new BadRequestException("AI 未返回分析结果");
        }
        return accumulated.toString();
    }

    private void emitStreamingProgress(
            String accumulated,
            String[] last,
            Consumer<ConversationAnalysisProgress> onProgress) {

        StreamingPreview preview = extractStreamingPreview(accumulated);
        boolean isNewItem = isNewStreamingItem(last[0], preview.original);

        ConversationAnalysisProgress.ConversationAnalysisProgressBuilder builder =
                ConversationAnalysisProgress.builder()
                        .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                        .message("正在接收 AI 分析结果...")
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
        try {
            String cleaned = stripMarkdownFence(raw);
            return objectMapper.readValue(cleaned, GrammarAnalysisResult.class);
        } catch (Exception ex) {
            log.warn("语法分析 JSON 解析失败: {}", ex.getMessage());
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
}
