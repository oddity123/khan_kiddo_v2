package com.khankiddo.learning.config;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM 厂商协议参数（对齐 v1 默认关闭豆包深度思考）。
 */
@Component
public class AiLlmProperties {

    private static final String THINKING_DISABLED = "disabled";

    /**
     * Chat Completions 请求体 {@code thinking} 字段（LangChain4j {@code customParameters}）。
     * 固定关闭，避免响应 {@code reasoning_content} 占满 max_tokens。
     */
    public Map<String, Object> thinkingCustomParameters() {
        return Map.of("thinking", Map.of("type", THINKING_DISABLED));
    }
}
