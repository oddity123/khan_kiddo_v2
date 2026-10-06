package com.khankiddo.learning.voice;

import com.khankiddo.learning.config.VoiceRealtimeProperties;
import com.khankiddo.learning.security.AuthenticatedUser;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 浏览器 ↔ 本服务 ↔ 火山 openspeech 的 JSON 文本帧双向代理。
 * 密钥仅出现在本服务到 openspeech 的连接上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceRealtimeProxyHandler extends TextWebSocketHandler {

    private static final String ATTR_UPSTREAM = "voice.realtime.upstream";
    private static final String ATTR_CLOSING = "voice.realtime.closing";
    /** 上游尚未就绪时暂存的客户端文本帧，就绪后按序 flush。 */
    private static final String ATTR_PENDING = "voice.realtime.pending";
    private static final int ERROR_PAYLOAD_LOG_LIMIT = 2000;

    private final VoiceRealtimeProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
        AuthenticatedUser user = (AuthenticatedUser) clientSession.getAttributes()
                .get(VoiceRealtimeHandshakeInterceptor.ATTR_USER);
        if (ObjectUtils.isEmpty(user)) {
            clientSession.close(CloseStatus.NOT_ACCEPTABLE.withReason("未登录"));
            return;
        }
        if (!properties.isConfigured()) {
            sendClientError(clientSession, "未配置 DOUBAO_SPEECH_API_KEY，无法建立实时语音会话");
            clientSession.close(CloseStatus.SERVICE_RESTARTED.withReason("speech api key missing"));
            return;
        }

        clientSessions.put(clientSession.getId(), clientSession);
        AtomicBoolean closing = new AtomicBoolean(false);
        clientSession.getAttributes().put(ATTR_CLOSING, closing);
        clientSession.getAttributes().put(ATTR_PENDING, new ArrayList<String>());

        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(20));
        applyUpstreamAuth(builder);

        builder.buildAsync(URI.create(properties.getWsUrl()), new UpstreamListener(clientSession, closing))
                .whenComplete((upstream, error) -> {
                    if (error != null) {
                        log.warn("openspeech WS connect failed userId={}: {}", user.id(), error.toString());
                        try {
                            sendClientError(clientSession, "连接豆包实时语音失败：" + rootMessage(error));
                            clientSession.close(CloseStatus.SERVER_ERROR);
                        } catch (IOException ignored) {
                            // ignore
                        }
                        return;
                    }
                    attachUpstreamAndFlush(clientSession, upstream);
                    log.info("voice realtime proxy ready userId={} clientSession={}", user.id(), clientSession.getId());
                });
    }

    @Override
    protected void handleTextMessage(WebSocketSession clientSession, TextMessage message) {
        String payload = message.getPayload();
        if (!StringUtils.hasText(payload)) {
            return;
        }
        synchronized (clientSession) {
            WebSocket upstream = (WebSocket) clientSession.getAttributes().get(ATTR_UPSTREAM);
            if (upstream == null) {
                @SuppressWarnings("unchecked")
                List<String> pending = (List<String>) clientSession.getAttributes().get(ATTR_PENDING);
                if (pending != null) {
                    pending.add(payload);
                    return;
                }
                try {
                    sendClientError(clientSession, "上游连接尚未就绪，请稍候再试");
                } catch (IOException ignored) {
                    // ignore
                }
                return;
            }
            upstream.sendText(payload, true);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) {
        clientSessions.remove(clientSession.getId());
        markClosing(clientSession);
        WebSocket upstream;
        synchronized (clientSession) {
            clientSession.getAttributes().remove(ATTR_PENDING);
            upstream = (WebSocket) clientSession.getAttributes().remove(ATTR_UPSTREAM);
        }
        if (upstream != null) {
            try {
                upstream.sendClose(WebSocket.NORMAL_CLOSURE, "client closed");
            } catch (Exception e) {
                log.debug("upstream close after client disconnect: {}", e.toString());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) {
        log.warn("client voice WS transport error session={}: {}", clientSession.getId(), exception.toString());
        markClosing(clientSession);
        try {
            clientSession.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // ignore
        }
    }

    @PreDestroy
    void shutdown() {
        for (WebSocketSession session : clientSessions.values()) {
            try {
                session.close(CloseStatus.GOING_AWAY);
            } catch (IOException ignored) {
                // ignore
            }
        }
        clientSessions.clear();
    }

    private void attachUpstreamAndFlush(WebSocketSession clientSession, WebSocket upstream) {
        synchronized (clientSession) {
            clientSession.getAttributes().put(ATTR_UPSTREAM, upstream);
            @SuppressWarnings("unchecked")
            List<String> pending = (List<String>) clientSession.getAttributes().remove(ATTR_PENDING);
            if (CollectionUtils.isEmpty(pending)) {
                return;
            }
            for (String buffered : pending) {
                if (StringUtils.hasText(buffered)) {
                    upstream.sendText(buffered, true);
                }
            }
        }
    }

    private void applyUpstreamAuth(WebSocket.Builder builder) {
        if (properties.usesNewApiKey()) {
            builder.header("X-Api-Key", properties.getApiKey().trim());
            return;
        }
        builder.header("X-Api-App-Id", properties.getAppId().trim());
        builder.header("X-Api-Access-Key", properties.getAccessKey().trim());
        builder.header("X-Api-Resource-Id",
                StringUtils.hasText(properties.getResourceId())
                        ? properties.getResourceId().trim()
                        : VoiceRealtimeProperties.DEFAULT_RESOURCE_ID);
        builder.header("X-Api-Request-Id", UUID.randomUUID().toString());
    }

    private static void markClosing(WebSocketSession clientSession) {
        Object flag = clientSession.getAttributes().get(ATTR_CLOSING);
        if (flag instanceof AtomicBoolean closing) {
            closing.set(true);
        }
    }

    private static void sendClientError(WebSocketSession clientSession, String message) throws IOException {
        if (!clientSession.isOpen()) {
            return;
        }
        String safe = message == null ? "unknown error" : message.replace("\"", "'");
        clientSession.sendMessage(new TextMessage(
                "{\"type\":\"error\",\"message\":\"" + safe + "\"}"));
    }

    private static String rootMessage(Throwable error) {
        Throwable cur = error;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return StringUtils.hasText(cur.getMessage()) ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    static String truncateForLog(String payload, int limit) {
        if (!StringUtils.hasText(payload) || payload.length() <= limit) {
            return payload;
        }
        return payload.substring(0, limit) + "...(truncated)";
    }

    static boolean looksLikeUpstreamErrorPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        return payload.contains("\"type\":\"error\"") || payload.contains("\"type\": \"error\"");
    }

    private final class UpstreamListener implements WebSocket.Listener {
        private final WebSocketSession clientSession;
        private final AtomicBoolean closing;
        private final StringBuilder textBuffer = new StringBuilder();

        private UpstreamListener(WebSocketSession clientSession, AtomicBoolean closing) {
            this.clientSession = clientSession;
            this.closing = closing;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String payload = textBuffer.toString();
                textBuffer.setLength(0);
                forwardToClient(payload);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // 协议约定纯 JSON 文本帧；忽略意外二进制
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!closing.get() && clientSession.isOpen()) {
                try {
                    clientSession.close(new CloseStatus(statusCode, reason == null ? "" : reason));
                } catch (IOException e) {
                    log.debug("close client after upstream close: {}", e.toString());
                }
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("openspeech upstream error session={}: {}", clientSession.getId(), error.toString());
            if (!closing.get() && clientSession.isOpen()) {
                try {
                    sendClientError(clientSession, "上游实时语音连接异常：" + rootMessage(error));
                    clientSession.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }

        private void forwardToClient(String payload) {
            if (!clientSession.isOpen() || !StringUtils.hasText(payload)) {
                return;
            }
            if (looksLikeUpstreamErrorPayload(payload)) {
                log.warn("openspeech error payload session={}: {}",
                        clientSession.getId(), truncateForLog(payload, ERROR_PAYLOAD_LOG_LIMIT));
            }
            try {
                synchronized (clientSession) {
                    clientSession.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.debug("forward upstream→client failed: {}", e.toString());
            }
        }
    }
}
