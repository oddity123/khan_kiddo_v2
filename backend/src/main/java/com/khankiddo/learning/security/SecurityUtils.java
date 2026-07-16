package com.khankiddo.learning.security;

import com.khankiddo.learning.exception.UnauthorizedException;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
