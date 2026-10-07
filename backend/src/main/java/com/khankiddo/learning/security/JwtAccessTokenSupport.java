package com.khankiddo.learning.security;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 从查询参数解析 JWT（浏览器原生 WebSocket 不便带 Authorization 头）。
 * Filter 与 WebSocket HandshakeInterceptor 共用，禁止各处再复制一份。
 */
public final class JwtAccessTokenSupport {

    public static final String ACCESS_TOKEN_QUERY_PARAM = "access_token";

    private JwtAccessTokenSupport() {
    }

    /**
     * 从 Servlet 请求查询参数读取 {@link #ACCESS_TOKEN_QUERY_PARAM}；无则返回 {@code null}。
     */
    public static String resolveFromServletRequest(HttpServletRequest request) {
        if (ObjectUtils.isEmpty(request)) {
            return null;
        }
        return trimToNull(request.getParameter(ACCESS_TOKEN_QUERY_PARAM));
    }

    /**
     * 从 Spring {@link ServerHttpRequest} 解析 access_token（优先 Servlet 参数，否则手拆 query）。
     */
    public static String resolveFromServerHttpRequest(ServerHttpRequest request) {
        if (ObjectUtils.isEmpty(request)) {
            return null;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String fromQuery = resolveFromServletRequest(servletRequest.getServletRequest());
            if (StringUtils.hasText(fromQuery)) {
                return fromQuery;
            }
        }
        return resolveFromQueryString(request.getURI().getQuery());
    }

    /**
     * 从原始 query 字符串解析 {@code access_token}（URL 解码）；无则返回 {@code null}。
     */
    public static String resolveFromQueryString(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq);
            if (ACCESS_TOKEN_QUERY_PARAM.equals(key)) {
                return trimToNull(URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
