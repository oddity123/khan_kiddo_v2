package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.conversation-analysis")
public class ConversationAnalysisProperties {

    private int minContentLength = 10;
    private int maxContentLength = 10000;
    /**
     * 用户句数超过此值时启用分批分析
     */
    private int batchThreshold = 15;

    /**
     * 每批用户句数（ceil(n / batchSize) 批，最后一批可不足 batchSize）
     */
    private int batchSize = 5;

    /**
     * 分批分析最大并发批数
     */
    private int batchConcurrentLimit = 5;

    /** Stage 1 对话分离模型（Flash，对齐 v1 LlmModelKind.FLASH） */
    private String separationModelName = "doubao-seed-1-6-flash-250828";

    /** Stage 1 对话分离 temperature（v1 使用 0.3） */
    private double separationTemperature = 0.3;

    /**
     * 是否在 API 层启用 JSON Schema 严格模式（response_format json_schema + strict）。
     * 关闭时仅依赖 prompt，易出现未转义字符导致 Jackson 解析失败。
     */
    private boolean strictJsonSchema = true;

    /** 是否启用对话分析限流 */
    private boolean analyzeRateLimitEnabled = true;

    /** 限流窗口内允许的最大请求数 */
    private int analyzeRateLimitMaxRequests = 5;

    /** 限流窗口长度（分钟） */
    private int analyzeRateLimitWindowMinutes = 1;
}
