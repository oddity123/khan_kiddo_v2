package com.khankiddo.learning.conversation;

import org.springframework.util.StringUtils;

/**
 * 解析 Stage2 {@code point} / {@code errorPoint} 中的「wrong → correction（中文原因）」片段。
 */
public final class ErrorPointSpanSupport {

    private static final char[] ARROWS = {'\u2192', '\u21d2'};

    private ErrorPointSpanSupport() {
    }

    /**
     * @return wrong/correct；无箭头或两侧为空时返回 {@code null}
     */
    public static Span parse(String point) {
        if (!StringUtils.hasText(point)) {
            return null;
        }
        int arrowIdx = -1;
        for (char arrow : ARROWS) {
            int idx = point.indexOf(arrow);
            if (idx >= 0 && (arrowIdx < 0 || idx < arrowIdx)) {
                arrowIdx = idx;
            }
        }
        if (arrowIdx < 0) {
            return null;
        }
        String wrong = point.substring(0, arrowIdx).trim();
        String rest = point.substring(arrowIdx + 1).trim();
        int cut = rest.length();
        for (char paren : new char[]{'\uff08', '('}) {
            int idx = rest.indexOf(paren);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        String correct = rest.substring(0, cut).trim();
        if (!StringUtils.hasText(wrong) || !StringUtils.hasText(correct)) {
            return null;
        }
        return new Span(wrong, correct);
    }

    public record Span(String wrong, String correct) {
    }
}
