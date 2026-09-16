package com.khankiddo.learning.growth;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 焦点短语文本对立：去标点归一化与「实质差异」判定。
 * 过滤层与启发式切分层共用，避免 Filter 反向依赖 Cutter。
 */
public final class FocusPhraseTextSupport {

    private FocusPhraseTextSupport() {
    }

    /**
     * 两侧归一化后均非空且不相等，视为有实质差异（忽略大小写与标点）。
     */
    public static boolean hasSubstantiveDiff(String left, String right) {
        String a = normalizeKey(left);
        String b = normalizeKey(right);
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        return !a.equals(b);
    }

    /**
     * 小写 + 仅保留字母数字，其它字符压成单词间空格。
     */
    public static String normalizeKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean pendingSpace = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && sb.length() > 0) {
                    sb.append(' ');
                }
                pendingSpace = false;
                sb.append(c);
            } else if (sb.length() > 0) {
                pendingSpace = true;
            }
        }
        return sb.toString();
    }
}
