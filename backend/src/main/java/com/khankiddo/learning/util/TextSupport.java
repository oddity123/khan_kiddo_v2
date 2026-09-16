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
}
