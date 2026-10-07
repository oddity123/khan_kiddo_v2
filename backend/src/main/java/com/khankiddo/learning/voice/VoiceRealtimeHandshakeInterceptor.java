package com.khankiddo.learning.voice;

import com.khankiddo.learning.config.VoiceRealtimeProperties;
import com.khankiddo.learning.security.AuthenticatedUser;
import com.khankiddo.learning.security.JwtAccessTokenSupport;
import com.khankiddo.learning.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器原生 WebSocket 无法方便地带 Authorization 头，
 * 因此使用查询参数 {@link JwtAccessTokenSupport#ACCESS_TOKEN_QUERY_PARAM}（JWT）。
 */
@Component
@RequiredArgsConstructor
public class VoiceRealtimeHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = "voice.realtime.user";

    private final JwtService jwtService;
    private final VoiceRealtimeProperties properties;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!properties.isConfigured()) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        String token = JwtAccessTokenSupport.resolveFromServerHttpRequest(request);
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        AuthenticatedUser user = jwtService.parseToken(token).orElse(null);
        if (ObjectUtils.isEmpty(user)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USER, user);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
