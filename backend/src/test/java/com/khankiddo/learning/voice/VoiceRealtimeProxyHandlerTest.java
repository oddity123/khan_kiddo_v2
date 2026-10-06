package com.khankiddo.learning.voice;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void enqueuePendingLocked_buffersWhenUpstreamMissing_andNeverNeedsReadyError() {
        WebSocketSession session = mock(WebSocketSession.class);
        java.util.Map<String, Object> attrs = new java.util.concurrent.ConcurrentHashMap<>();
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn("s1");

        int n1 = VoiceRealtimeProxyHandler.enqueuePendingLocked(session, "{\"type\":\"session.create\"}");
        int n2 = VoiceRealtimeProxyHandler.enqueuePendingLocked(session, "{\"type\":\"input_audio_buffer.append\"}");

        assertThat(n1).isEqualTo(1);
        assertThat(n2).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<String> pending = (List<String>) attrs.get(VoiceRealtimeProxyHandler.ATTR_PENDING);
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0)).contains("session.create");
        // 硬保证：缓冲路径不依赖「尚未就绪」文案
        assertThat(pending.toString()).doesNotContain("上游连接尚未就绪");
    }

    @Test
    void flushPendingToUpstream_preservesOrder() {
        WebSocket upstream = mock(WebSocket.class);
        when(upstream.sendText(any(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(null));

        List<String> pending = new ArrayList<>();
        pending.add("{\"type\":\"session.create\"}");
        pending.add("{\"type\":\"input_audio_buffer.append\"}");

        int flushed = VoiceRealtimeProxyHandler.flushPendingToUpstream(upstream, pending);
        assertThat(flushed).isEqualTo(2);
        verify(upstream, times(1)).sendText(eq("{\"type\":\"session.create\"}"), eq(true));
        verify(upstream, times(1)).sendText(eq("{\"type\":\"input_audio_buffer.append\"}"), eq(true));
    }

    @Test
    void flushPendingToUpstream_noopWhenEmpty() {
        WebSocket upstream = mock(WebSocket.class);
        assertThat(VoiceRealtimeProxyHandler.flushPendingToUpstream(upstream, List.of())).isZero();
        verify(upstream, never()).sendText(any(), anyBoolean());
    }

    @Test
    void proxyReadyPayload_isStable() {
        assertThat(VoiceRealtimeProxyHandler.PROXY_READY_PAYLOAD).isEqualTo("{\"type\":\"proxy.ready\"}");
    }

    @Test
    void sourceMustNotEmitUpstreamNotReadyChineseError() throws Exception {
        // 回归：用户帧里的这句中文不得再作为 session.create 的即时错误返回
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/khankiddo/learning/voice/VoiceRealtimeProxyHandler.java"));
        assertThat(src).doesNotContain("上游连接尚未就绪，请稍候再试");
    }
}
