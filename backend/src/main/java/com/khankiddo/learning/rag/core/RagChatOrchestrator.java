package com.khankiddo.learning.rag.core;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG 问答编排：检索片段注入 prompt 后流式生成。
 */
@Component
public class RagChatOrchestrator {

    public void streamAnswer(
            StreamingChatModel streamingChatModel,
            String systemPrompt,
            String userQuestion,
            List<EmbeddingMatch<TextSegment>> matches,
            StreamingChatResponseHandler handler) {
        streamAnswer(streamingChatModel, systemPrompt, userQuestion, null, null, matches, handler);
    }

    public void streamAnswer(
            StreamingChatModel streamingChatModel,
            String systemPrompt,
            String userQuestion,
            String retrievalStrategy,
            String statsSummary,
            List<EmbeddingMatch<TextSegment>> matches,
            StreamingChatResponseHandler handler) {
        streamAnswer(streamingChatModel, systemPrompt, userQuestion, retrievalStrategy, statsSummary, matches, null, handler);
    }

    public void streamAnswer(
            StreamingChatModel streamingChatModel,
            String systemPrompt,
            String userQuestion,
            String retrievalStrategy,
            String statsSummary,
            List<EmbeddingMatch<TextSegment>> matches,
            List<String> matchLabels,
            StreamingChatResponseHandler handler) {
        String userPrompt = buildUserPrompt(userQuestion, retrievalStrategy, statsSummary, matches, matchLabels);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .build();
        streamingChatModel.chat(chatRequest, handler);
    }

    public String buildContextBlock(List<EmbeddingMatch<TextSegment>> matches) {
        return buildContextBlock(matches, null);
    }

    public String buildContextBlock(List<EmbeddingMatch<TextSegment>> matches, List<String> matchLabels) {
        if (CollectionUtils.isEmpty(matches)) {
            return "（未检索到相关历史记录）";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            builder.append("【片段 ").append(index++);
            if (!CollectionUtils.isEmpty(matchLabels) && i < matchLabels.size()
                    && StringUtils.hasText(matchLabels.get(i))) {
                builder.append(" · ").append(matchLabels.get(i));
            }
            builder.append("】\n");
            builder.append(match.embedded().text()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildUserPrompt(
            String userQuestion,
            String retrievalStrategy,
            String statsSummary,
            List<EmbeddingMatch<TextSegment>> matches,
            List<String> matchLabels) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(userQuestion.trim()).append("\n\n");
        if (StringUtils.hasText(retrievalStrategy)) {
            builder.append("检索策略：\n").append(retrievalStrategy.trim()).append("\n\n");
        }
        if (StringUtils.hasText(statsSummary)) {
            builder.append("历史错误类型统计：\n").append(statsSummary.trim()).append("\n\n");
        }
        builder.append("检索到的历史语法错误记录：\n").append(buildContextBlock(matches, matchLabels));
        return builder.toString().trim();
    }
}
