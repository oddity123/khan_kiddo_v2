package com.khankiddo.learning.growth;

/**
 * 将 Phrase Review {@code reason} 写入成长卡 {@code evidence_json}（不扩 DDL）。
 */
public final class GrowthCardReasonEvidence {

    private GrowthCardReasonEvidence() {
    }

    /**
     * @return {@code {"reason":"…"}} JSON，或 reason 为空时 {@code null}
     */
    public static String toEvidenceJson(String reason) {
        if (!org.springframework.util.StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        StringBuilder sb = new StringBuilder(trimmed.length() + 16);
        sb.append("{\"reason\":\"");
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"}");
        return sb.toString();
    }
}
