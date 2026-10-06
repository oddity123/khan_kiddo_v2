package com.khankiddo.learning.util;

import org.springframework.util.StringUtils;

/**
 * 通用文本小工具：空白 trim 后空串视为 {@code null}。
 */
public final class TextSupport {

    private TextSupport() {
    }

    /** trim；无有效文本时返回 {@code null}。 */
    public static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /** 服务根地址去掉尾部所有 {@code /}；无有效文本时返回 {@code fallback}。 */
    public static String trimTrailingSlashes(String url, String fallback) {
        if (!StringUtils.hasText(url)) {
            return fallback;
        }
        String trimmed = url.trim();
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(0, end);
    }
}
