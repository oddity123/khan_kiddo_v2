package com.khankiddo.learning.config;

import com.khankiddo.learning.config.condition.OnQwenConfiguredCondition;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain for Java Easy RAG：注册延迟初始化的 {@link ContentRetriever} Bean。
 */
@Configuration
@Conditional(OnQwenConfiguredCondition.class)
@RequiredArgsConstructor
public class Langchain4jLearningRagConfig {

    private final Langchain4jLearningRagInitializer ragInitializer;

    @Bean("langchain4jLearningContentRetriever")
    public ContentRetriever langchain4jLearningContentRetriever() {
        return ragInitializer::retrieve;
    }
}
