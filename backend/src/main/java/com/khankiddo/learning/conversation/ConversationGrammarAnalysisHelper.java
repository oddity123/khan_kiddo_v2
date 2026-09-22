package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

/**
 * Stage 2 主分析：LangChain4j 非流式调用语法 JSON。
 */
@Slf4j
@Component
public class ConversationGrammarAnalysisHelper {

    private static final int GRAMMAR_CHAT_MAX_ATTEMPTS =
            ConversationAnalysisTimeoutBudget.GRAMMAR_CHAT_MAX_ATTEMPTS;

    private final LlmChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;

    public ConversationGrammarAnalysisHelper(
            LlmChatModelFactory chatModelFactory,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
    }

    public GrammarAnalysisResult analyzeGrammar(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            Consumer<ConversationAnalysisProgress> onProgress) {
        return analyzeGrammar(systemPrompt, userPrompt, model, 0, 0, onProgress);
    }

    public GrammarAnalysisResult analyzeGrammar(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
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
                .build());

        GrammarAnalysisResult result = null;
        for (int attempt = 1; attempt <= GRAMMAR_CHAT_MAX_ATTEMPTS; attempt++) {
            GrammarJsonResponse response = fetchGrammarJson(
                    systemPrompt, userPrompt, model, attempt, batchNum, totalBatches);
            try {
                result = parseGrammarJson(response.text(), response.finishReason());
                break;
            } catch (BadRequestException ex) {
                // 截断再打通常仍会 LENGTH，不重试
                if (response.finishReason() == FinishReason.LENGTH
                        || attempt >= GRAMMAR_CHAT_MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("语法分析 JSON 解析失败，准备重试 ({}/{}): finishReason={}, length={}",
                        attempt, GRAMMAR_CHAT_MAX_ATTEMPTS, response.finishReason(),
                        response.text().length());
            }
        }
        return result;
    }

    private static ConversationAnalysisProgress withBatchPrefix(
            ConversationAnalysisProgress progress,
            int batchNum,
            int totalBatches) {

        String tag = "[" + batchNum + "/" + totalBatches + "] ";
        return ConversationAnalysisProgress.builder()
                .status(progress.getStatus())
                .message(prefixIfHasText(progress.getMessage(), tag))
                .result(progress.getResult())
                .errorMessage(progress.getErrorMessage())
                .messageStats(progress.getMessageStats())
                .build();
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

    private GrammarJsonResponse loggedGrammarCall(
            ResolvedLlmModel model,
            int attempt,
            int batchNum,
            int totalBatches,
            java.util.function.Supplier<GrammarJsonResponse> call) {
        long startedAt = System.currentTimeMillis();
        try {
            GrammarJsonResponse response = call.get();
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_GRAMMAR,
                    model != null ? model.getId() : null,
                    attempt,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.RESULT_OK,
                    batchNum,
                    totalBatches,
                    ConversationAnalysisCallLog.MODE_CHAT);
            return response;
        } catch (RuntimeException ex) {
            ConversationAnalysisCallLog.record(
                    ConversationAnalysisCallLog.STAGE_GRAMMAR,
                    model != null ? model.getId() : null,
                    attempt,
                    System.currentTimeMillis() - startedAt,
                    ConversationAnalysisCallLog.resultOf(ex),
                    batchNum,
                    totalBatches,
                    ConversationAnalysisCallLog.MODE_CHAT);
            throw ex;
        }
    }

    private GrammarJsonResponse fetchGrammarJson(
            String systemPrompt,
            String userPrompt,
            ResolvedLlmModel model,
            int attempt,
            int batchNum,
            int totalBatches) {

        return loggedGrammarCall(
                model,
                attempt,
                batchNum,
                totalBatches,
                () -> doFetchGrammarJson(systemPrompt, userPrompt, model));
    }

    private GrammarJsonResponse doFetchGrammarJson(
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
        return new GrammarJsonResponse(response.aiMessage().text(), response.finishReason());
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

    private record GrammarJsonResponse(String text, FinishReason finishReason) {
    }
}
