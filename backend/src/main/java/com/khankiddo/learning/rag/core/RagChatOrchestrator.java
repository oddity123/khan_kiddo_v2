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
        String userPrompt = buildUserPrompt(userQuestion, matches);
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .build();
        streamingChatModel.chat(chatRequest, handler);
    }

    public String buildContextBlock(List<EmbeddingMatch<TextSegment>> matches) {
        if (CollectionUtils.isEmpty(matches)) {
            return "（未检索到相关历史记录）";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (EmbeddingMatch<TextSegment> match : matches) {
            builder.append("【片段 ").append(index++).append("】\n");
            builder.append(match.embedded().text()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildUserPrompt(String userQuestion, List<EmbeddingMatch<TextSegment>> matches) {
        return """
                用户问题：
                %s

                检索到的历史语法错误记录：
                %s
                """.formatted(userQuestion.trim(), buildContextBlock(matches));
    }
}
