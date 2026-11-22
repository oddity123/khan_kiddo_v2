package com.khankiddo.learning.weaknessmemory;

import org.springframework.util.StringUtils;

/**
 * 薄弱点记忆 title 落库前校验：单病灶、非教条、长度有界。
 */
public final class WeaknessMemoryTitleSupport {

    /** 硬上限：过长报告腔直接拒绝。 */
    public static final int MAX_TITLE_LENGTH = 48;

    private WeaknessMemoryTitleSupport() {
    }

    /**
     * @return 可落库的 trim 后 title；不可接受时返回 null
     */
    public static String sanitizeOrNull(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return null;
        }
        String title = rawTitle.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            return null;
        }
        if (containsCompoundSeparator(title)) {
            return null;
        }
        if (looksLikeCoaching(title)) {
            return null;
        }
        return title;
    }

    static boolean containsCompoundSeparator(String title) {
        return title.indexOf('；') >= 0 || title.indexOf(';') >= 0;
    }

    static boolean looksLikeCoaching(String title) {
        return title.contains("开口前") || title.contains("不要");
    }
}
