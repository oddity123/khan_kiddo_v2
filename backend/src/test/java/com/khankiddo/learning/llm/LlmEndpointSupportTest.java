package com.khankiddo.learning.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmEndpointSupportTest {

    @Test
    void normalizeDoubaoBaseUrl_stripsTrailingSlash() {
        assertEquals(
                LlmEndpointSupport.DEFAULT_DOUBAO_BASE_URL,
                LlmEndpointSupport.normalizeDoubaoBaseUrl("  "));
        assertEquals(
                "https://example.com/v1",
                LlmEndpointSupport.normalizeDoubaoBaseUrl("https://example.com/v1/"));
    }

    @Test
    void normalizeDashScopeBaseUrl_defaultWhenBlank() {
        assertEquals(
                LlmEndpointSupport.DEFAULT_DASHSCOPE_BASE_URL,
                LlmEndpointSupport.normalizeDashScopeBaseUrl(null));
    }
}
