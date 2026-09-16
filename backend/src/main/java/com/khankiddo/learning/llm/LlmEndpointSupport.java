package com.khankiddo.learning.llm;

import org.springframework.util.StringUtils;

/**
 * LLM / 兼容 OpenAI 端点 URL 规范化（去尾斜杠；空则回落默认根地址）。
 */
public final class LlmEndpointSupport {

    public static final String DEFAULT_DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    public static final String DEFAULT_DASHSCOPE_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private LlmEndpointSupport() {
    }

    public static String normalizeBaseUrl(String baseUrl, String defaultWhenBlank) {
        if (!StringUtils.hasText(baseUrl)) {
            return defaultWhenBlank;
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public static String normalizeDoubaoBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl, DEFAULT_DOUBAO_BASE_URL);
    }

    public static String normalizeDashScopeBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl, DEFAULT_DASHSCOPE_BASE_URL);
    }
}
