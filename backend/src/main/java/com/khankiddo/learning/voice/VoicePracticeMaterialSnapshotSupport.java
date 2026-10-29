package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 材料快照 JSON 编解码与规范化（去重、上限）。归属校验在 Service 层。
 */
@Component
@RequiredArgsConstructor
public class VoicePracticeMaterialSnapshotSupport {

    public static final int MAX_CARD_IDS = 10;
    public static final int MAX_ANALYSIS_IDS = 5;

    private final ObjectMapper objectMapper;

    public VoicePracticeMaterialSnapshot normalize(VoicePracticeMaterialSnapshot raw) {
        List<String> cards = uniqueTrimmed(raw == null ? null : raw.cardIds(), MAX_CARD_IDS, "成长卡");
        List<String> analyses = uniqueTrimmed(raw == null ? null : raw.analysisIds(), MAX_ANALYSIS_IDS, "分析记录");
        return new VoicePracticeMaterialSnapshot(cards, analyses);
    }

    public String toJson(VoicePracticeMaterialSnapshot snapshot) throws Exception {
        VoicePracticeMaterialSnapshot snap = snapshot == null
                ? VoicePracticeMaterialSnapshot.empty()
                : snapshot;
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode cards = root.putArray("cardIds");
        for (String id : snap.cardIds()) {
            cards.add(id);
        }
        ArrayNode analyses = root.putArray("analysisIds");
        for (String id : snap.analysisIds()) {
            analyses.add(id);
        }
        return objectMapper.writeValueAsString(root);
    }

    public VoicePracticeMaterialSnapshot fromJson(String json) throws Exception {
        if (!StringUtils.hasText(json)) {
            return VoicePracticeMaterialSnapshot.empty();
        }
        JsonNode root = objectMapper.readTree(json);
        List<String> cards = readStringArray(root.get("cardIds"));
        List<String> analyses = readStringArray(root.get("analysisIds"));
        return normalize(new VoicePracticeMaterialSnapshot(cards, analyses));
    }

    private static List<String> uniqueTrimmed(List<String> raw, int max, String label) {
        if (CollectionUtils.isEmpty(raw)) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String item : raw) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            seen.add(item.trim());
        }
        if (seen.size() > max) {
            throw new IllegalArgumentException("本场最多选择 " + max + " 个" + label);
        }
        return List.copyOf(seen);
    }

    private static List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                out.add(item.asText());
            }
        }
        return out;
    }
}
