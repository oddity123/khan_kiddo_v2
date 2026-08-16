package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    /**
     * HTTP 无数据读超时（流式与非流式 socket 空闲）。
     */
    private Duration httpReadTimeout = Duration.ofSeconds(45);

    /**
     * 流式整段生成墙钟上限（latch.await），允许持续出 token。
     */
    private Duration streamWallClockTimeout = Duration.ofMinutes(5);

    /**
     * 非流式单次调用超时。LangChain4j HTTP 重试关闭后，业务层各自再试。
     */
    private Duration chatTimeout = Duration.ofSeconds(60);

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

    /** 是否启用语法分析结果的确定性校验层（span 校验 + 自我修正过滤） */
    private boolean sanitizerEnabled = true;

    /** 校验层：原文片段不在原句中时剔除该错误（治 suggestion/errors 脱节与假阳性） */
    private boolean sanitizerDropUnmatchedSpan = true;

    /** 校验层：口语「先说错、紧接着改对」的自我修正剔除（如 It have It has） */
    private boolean sanitizerDropSelfCorrection = true;

    /** 游客免登录可分析次数（按 guest Cookie，进程内计数） */
    private int guestFreeAnalyzeLimit = 3;

    /** 游客身份 Cookie 名 */
    private String guestCookieName = "kk_guest_id";

    /** 游客 Cookie 有效天数 */
    private int guestCookieMaxAgeDays = 365;
}
