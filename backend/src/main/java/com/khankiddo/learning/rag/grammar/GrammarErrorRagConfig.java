package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.LlmModelCatalog;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorRagConfig {

    public static final String GRAMMAR_RAG_STREAMING_CHAT_MODEL = "grammarRagStreamingChatModel";
    private static final String QWEN_PLUS_MODEL_ID = "qwen-plus";

    @Bean(GRAMMAR_RAG_STREAMING_CHAT_MODEL)
    public StreamingChatModel grammarRagStreamingChatModel(
            LlmChatModelFactory chatModelFactory,
            LlmModelCatalog modelCatalog) {
        return chatModelFactory.streaming(modelCatalog.resolveRequired(QWEN_PLUS_MODEL_ID));
    }
}
