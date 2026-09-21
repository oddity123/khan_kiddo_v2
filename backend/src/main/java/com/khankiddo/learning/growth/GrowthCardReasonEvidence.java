package com.khankiddo.learning.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 将 Phrase Review {@code reason} 写入成长卡 {@code evidence_json}（不扩 DDL），并在读卡时解析回 DTO。
 */
public final class GrowthCardReasonEvidence {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GrowthCardReasonEvidence() {
    }

    /**
     * @return {@code {"reason":"…"}} JSON，或 reason 为空时 {@code null}
     */
    public static String toEvidenceJson(String reason) {
        if (!StringUtils.hasText(reason)) {
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

    /**
     * 从成长卡 {@code evidence_json} 解析 {@code reason}；缺失或非法 JSON 时返回 {@code null}。
     */
    public static String parseReason(String evidenceJson) {
        if (!StringUtils.hasText(evidenceJson)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(evidenceJson.trim());
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode reasonNode = root.get("reason");
            if (reasonNode == null || reasonNode.isNull() || !reasonNode.isTextual()) {
                return null;
            }
            String text = reasonNode.asText();
            return StringUtils.hasText(text) ? text.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
