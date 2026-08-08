package com.khankiddo.learning.rag.core;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 语法 RAG 嵌入模型：只依赖 {@link RagProperties} / {@code QWEN_*}，
 * 不经过对话分析用的 {@code LlmModelCatalog}。
 */
@Configuration
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class QwenEmbeddingModelFactory {

    private final RagProperties ragProperties;

    @Bean
    public EmbeddingModel ragEmbeddingModel() {
        if (!StringUtils.hasText(ragProperties.getEmbeddingApiKey())) {
            throw new IllegalStateException(
                    "RAG 已启用但未配置 app.rag.embedding-api-key（通常为 QWEN_API_KEY）");
        }
        return OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(ragProperties.getEmbeddingBaseUrl()))
                .apiKey(ragProperties.getEmbeddingApiKey().trim())
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
