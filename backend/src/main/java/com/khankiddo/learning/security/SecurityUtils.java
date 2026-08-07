package com.khankiddo.learning.security;

import com.khankiddo.learning.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * 当前登录用户访问入口。需「必须已登录」时用 {@link #requireUserId()} / {@link #requireCurrentUser()}，
 * 禁止在各 Service/Tool 内再复制一份 requireUserId。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    public static Long getCurrentUserId() {
        AuthenticatedUser user = getCurrentUser();
        return user != null ? user.id() : null;
    }

    /**
     * 要求已登录，否则抛 {@link UnauthorizedException}。
     */
    public static Long requireUserId() {
        Long userId = getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }

    /**
     * 要求已登录并返回用户主体，否则抛 {@link UnauthorizedException}。
     */
    public static AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser user = getCurrentUser();
        if (ObjectUtils.isEmpty(user)) {
            throw new UnauthorizedException("未登录");
        }
        return user;
    }

    /**
     * 请求带了 Bearer 但未能解析为登录用户（过期/伪造）时拒绝，避免被当成游客。
     */
    public static void rejectStaleBearer(HttpServletRequest request) {
        if (ObjectUtils.isEmpty(request)) {
            return;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return;
        }
        String token = header.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (ObjectUtils.isEmpty(getCurrentUserId())) {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }
    }
}
