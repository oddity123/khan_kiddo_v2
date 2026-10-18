package com.khankiddo.learning.voice;

import com.khankiddo.learning.config.VoiceRealtimeProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.security.AuthenticatedUser;
import com.khankiddo.learning.security.JwtAccessTokenSupport;
import com.khankiddo.learning.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器原生 WebSocket 无法方便地带 Authorization 头，
 * 因此使用查询参数 {@link JwtAccessTokenSupport#ACCESS_TOKEN_QUERY_PARAM}（JWT）。
 */
@Component
@RequiredArgsConstructor
public class VoiceRealtimeHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = "voice.realtime.user";
    public static final String ATTR_PRACTICE_SESSION_ID = "voice.practice.sessionId";
    public static final String ATTR_GRANTED_SECONDS = "voice.practice.grantedSeconds";
    public static final String PRACTICE_SESSION_ID_PARAM = "practice_session_id";

    private final JwtService jwtService;
    private final VoiceRealtimeProperties properties;
    private final VoicePracticeSessionService practiceSessions;

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
        Long practiceSessionId = resolvePracticeSessionId(request);
        if (practiceSessionId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        int grantedSeconds;
        try {
            grantedSeconds = practiceSessions.grantedSecondsForConnect(user.id(), practiceSessionId);
        } catch (BadRequestException ex) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        attributes.put(ATTR_USER, user);
        attributes.put(ATTR_PRACTICE_SESSION_ID, practiceSessionId);
        attributes.put(ATTR_GRANTED_SECONDS, grantedSeconds);
        return true;
    }

    private static Long resolvePracticeSessionId(ServerHttpRequest request) {
        String raw = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            raw = servletRequest.getServletRequest().getParameter(PRACTICE_SESSION_ID_PARAM);
        }
        if (!StringUtils.hasText(raw)) {
            raw = queryParam(request.getURI().getRawQuery(), PRACTICE_SESSION_ID_PARAM);
        }
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String queryParam(String query, String name) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (!name.equals(part.substring(0, eq))) {
                continue;
            }
            return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
        }
        return null;
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
