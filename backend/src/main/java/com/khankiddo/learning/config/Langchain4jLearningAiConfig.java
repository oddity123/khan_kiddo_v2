package com.khankiddo.learning.config;

import com.khankiddo.learning.ai.langchain4j.Langchain4jLearningAi;
import com.khankiddo.learning.config.condition.OnQwenConfiguredCondition;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(OnQwenConfiguredCondition.class)
public class Langchain4jLearningAiConfig {

    @Bean
    public Langchain4jLearningAi langchain4jLearningAi(
            @Qualifier(Langchain4jLearningModelConfig.QWEN_PLUS_STREAMING_CHAT_MODEL)
            StreamingChatModel streamingChatModel,
            @Qualifier("langchain4jLearningContentRetriever") ContentRetriever contentRetriever) {
        return AiServices.builder(Langchain4jLearningAi.class)
                .streamingChatModel(streamingChatModel)
                .contentRetriever(contentRetriever)
                .build();
    }
}
