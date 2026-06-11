package com.khankiddo.learning.config;

import com.khankiddo.learning.config.condition.OnQwenConfiguredCondition;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.LlmModelCatalog;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 「LangChain for Java 学习」专用千问流式模型 Bean。
 */
@Configuration
@Conditional(OnQwenConfiguredCondition.class)
public class Langchain4jLearningModelConfig {

    public static final String QWEN_PLUS_STREAMING_CHAT_MODEL = "qwenPlusStreamingChatModel";
    private static final String QWEN_PLUS_MODEL_ID = "qwen-plus";

    @Bean(QWEN_PLUS_STREAMING_CHAT_MODEL)
    public StreamingChatModel qwenPlusStreamingChatModel(
            LlmChatModelFactory chatModelFactory,
            LlmModelCatalog modelCatalog) {
        return chatModelFactory.streaming(modelCatalog.resolveRequired(QWEN_PLUS_MODEL_ID));
    }
}
