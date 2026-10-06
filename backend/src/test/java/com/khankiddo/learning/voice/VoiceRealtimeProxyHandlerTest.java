package com.khankiddo.learning.voice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceRealtimeProxyHandlerTest {

    @Test
    void looksLikeUpstreamErrorPayload_detectsCompactAndSpacedType() {
        assertThat(VoiceRealtimeProxyHandler.looksLikeUpstreamErrorPayload(
                "{\"type\":\"error\",\"error\":{\"message\":\"bad\",\"code\":123}}")).isTrue();
        assertThat(VoiceRealtimeProxyHandler.looksLikeUpstreamErrorPayload(
                "{ \"type\": \"error\", \"message\": \"x\" }")).isTrue();
        assertThat(VoiceRealtimeProxyHandler.looksLikeUpstreamErrorPayload(
                "{\"type\":\"session.created\"}")).isFalse();
        assertThat(VoiceRealtimeProxyHandler.looksLikeUpstreamErrorPayload("")).isFalse();
        assertThat(VoiceRealtimeProxyHandler.looksLikeUpstreamErrorPayload(null)).isFalse();
    }

    @Test
    void truncateForLog_capsAtLimit() {
        String longPayload = "a".repeat(2500);
        String truncated = VoiceRealtimeProxyHandler.truncateForLog(longPayload, 2000);
        assertThat(truncated).hasSize(2000 + "...(truncated)".length());
        assertThat(truncated).endsWith("...(truncated)");
        assertThat(VoiceRealtimeProxyHandler.truncateForLog("short", 2000)).isEqualTo("short");
    }
}
