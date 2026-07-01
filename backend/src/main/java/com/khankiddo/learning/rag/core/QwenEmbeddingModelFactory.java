package com.khankiddo.learning.rag.core;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class QwenEmbeddingModelFactory {

    private static final String QWEN_PLUS_MODEL_ID = "qwen-plus";

    private final RagProperties ragProperties;
    private final LlmModelCatalog modelCatalog;

    @Bean
    public EmbeddingModel ragEmbeddingModel() {
        ResolvedLlmModel qwen = modelCatalog.resolveRequired(QWEN_PLUS_MODEL_ID);
        var config = qwen.getConfig();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(modelCatalog.resolveApiKey(config))
                .modelName(ragProperties.getEmbeddingModelName())
                .maxSegmentsPerBatch(ragProperties.getEmbeddingMaxSegmentsPerBatch())
                .build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
