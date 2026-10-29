package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.VoicePracticeSessionMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeSession;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 本场材料：校验归属、读快照、拼装工具可读摘要（仅快照范围内）。
 */
@Component
@RequiredArgsConstructor
public class VoicePracticeMaterialAccess {

    private final VoicePracticeSessionMapper sessionMapper;
    private final VoicePracticeMaterialSnapshotSupport snapshotSupport;
    private final GrowthCardStore growthCardStore;
    private final ConversationAnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    public VoicePracticeMaterialSnapshot validateAndNormalize(
            long userId, VoicePracticeMaterialSnapshot raw) {
        VoicePracticeMaterialSnapshot snap;
        try {
            snap = snapshotSupport.normalize(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        for (String cardId : snap.cardIds()) {
            growthCardStore.findOwned(cardId, userId)
                    .orElseThrow(() -> new BadRequestException("成长卡不存在或不属于当前用户：" + cardId));
        }
        for (String analysisId : snap.analysisIds()) {
            analysisMapper.findByAnalysisIdAndUserId(analysisId, userId)
                    .orElseThrow(() -> new BadRequestException("分析记录不存在或不属于当前用户：" + analysisId));
        }
        return snap;
    }

    public VoicePracticeMaterialSnapshot requireSnapshot(long userId, long practiceSessionId) {
        VoicePracticeSession row = sessionMapper.findById(practiceSessionId).orElse(null);
        if (row == null || ObjectUtils.isEmpty(row.getUserId()) || row.getUserId() != userId) {
            throw new BadRequestException("练习记录不存在");
        }
        try {
            return snapshotSupport.fromJson(row.getMaterialSnapshot());
        } catch (Exception e) {
            throw new BadRequestException("本场材料快照损坏");
        }
    }

    /**
     * 供 {@code get_practice_materials} 回传模型：仅快照内卡片与分析摘要。
     */
    public String buildMaterialsToolJson(long userId, long practiceSessionId) throws Exception {
        VoicePracticeMaterialSnapshot snap = requireSnapshot(userId, practiceSessionId);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("practiceSessionId", practiceSessionId);
        ArrayNode cards = root.putArray("cards");
        for (String cardId : snap.cardIds()) {
            growthCardStore.findOwned(cardId, userId).ifPresent(card -> cards.add(cardNode(card)));
        }
        ArrayNode analyses = root.putArray("analyses");
        for (String analysisId : snap.analysisIds()) {
            analysisMapper.findByAnalysisIdAndUserId(analysisId, userId)
                    .ifPresent(analysis -> analyses.add(analysisNode(analysis)));
        }
        root.put("cardCount", cards.size());
        root.put("analysisCount", analyses.size());
        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode cardNode(GrowthCard card) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("cardId", card.getCardId());
        node.put("type", nullToEmpty(card.getType()));
        node.put("status", nullToEmpty(card.getStatus()));
        node.put("front", nullToEmpty(card.getFront()));
        node.put("back", nullToEmpty(card.getBack()));
        if (StringUtils.hasText(card.getSourceAnalysisId())) {
            node.put("sourceAnalysisId", card.getSourceAnalysisId());
        }
        return node;
    }

    private ObjectNode analysisNode(ConversationAnalysis analysis) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("analysisId", analysis.getAnalysisId());
        node.put("status", nullToEmpty(analysis.getStatus()));
        String summary = analysis.getEducationalSummary();
        if (StringUtils.hasText(summary)) {
            node.put("summaryPreview", truncate(summary.trim(), 280));
        }
        return node;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}
