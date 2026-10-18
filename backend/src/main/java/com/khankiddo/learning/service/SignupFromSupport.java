package com.khankiddo.learning.service;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 注册来源只留 query {@code from} 里安全的短标记，例如 {@code xhs-ielts}、{@code reactivate}。
 */
public final class SignupFromSupport {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private SignupFromSupport() {
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (!ALLOWED.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}
