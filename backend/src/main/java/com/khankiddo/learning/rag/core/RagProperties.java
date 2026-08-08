package com.khankiddo.learning.rag.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 通用嵌入配置（与对话分析 {@code app.llm.models} 目录解耦）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /**
     * DashScope OpenAI 兼容端点（嵌入与聊天可共用同一 base-url / key）。
     */
    private String embeddingBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /**
     * 嵌入 API Key（通常绑定 {@code QWEN_API_KEY}）。
     */
    private String embeddingApiKey;

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
