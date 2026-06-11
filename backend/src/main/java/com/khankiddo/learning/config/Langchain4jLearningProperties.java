package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LangChain for Java 学习（Easy RAG）检索与嵌入配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.langchain4j-learning")
public class Langchain4jLearningProperties {

    /**
     * 通义千问嵌入模型（DashScope OpenAI 兼容 /embeddings）。
     */
    private String embeddingModelName = "text-embedding-v3";

    /**
     * 单次嵌入请求最多片段数。通义千问限制 ≤ 10（见 DashScope InvalidParameter: batch size）。
     */
    private int embeddingMaxSegmentsPerBatch = 10;

    /**
     * 每次检索最多返回的片段数。
     */
    private int retrievalMaxResults = 5;

    /**
     * 相似度下限，低于此分数的片段不注入 prompt（避免 SQL 等工作碎片误命中）。
     */
    private double retrievalMinScore = 0.55;
}
