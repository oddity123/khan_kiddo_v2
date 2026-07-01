package com.khankiddo.learning.rag.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 通用嵌入配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private String embeddingModelName = "text-embedding-v3";

    /**
     * 单次嵌入请求最多片段数（通义千问限制 ≤ 10）。
     */
    private int embeddingMaxSegmentsPerBatch = 10;

    /**
     * text-embedding-v3 默认向量维度。
     */
    private int embeddingDimension = 1024;
}
