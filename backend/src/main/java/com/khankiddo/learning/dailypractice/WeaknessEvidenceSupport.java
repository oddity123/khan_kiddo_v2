package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.knowledge.KnowledgePointStatsSupport;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.UserWeaknessMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 读时按记忆锚点从错句明细取 1 条近例（不落记忆表）。
 */
@Component
@RequiredArgsConstructor
public class WeaknessEvidenceSupport {

    private final ConversationAnalysisItemMapper itemMapper;
    private final PointDictionary pointDictionary;

    public EvidenceLine findLatestEvidence(Long userId, UserWeaknessMemory memory) {
        if (userId == null || memory == null) {
            return EvidenceLine.empty();
        }
        List<String> pointIds = resolvePointIds(memory);
        if (CollectionUtils.isEmpty(pointIds)) {
            return EvidenceLine.empty();
        }
        // days=null：用 Java/LIMIT 取最新 1 条，避免 MySQL DATE_SUB 在 H2 测试库不可用
        List<ConversationAnalysisItem> rows = itemMapper.findErrorExamplesByUserId(
                userId, pointIds, null, 1, null);
        if (CollectionUtils.isEmpty(rows)) {
            return EvidenceLine.empty();
        }
        ConversationAnalysisItem item = rows.get(0);
        return new EvidenceLine(
                StringUtils.hasText(item.getOriginalSentence()) ? item.getOriginalSentence().trim() : null,
                StringUtils.hasText(item.getSuggestion()) ? item.getSuggestion().trim() : null);
    }

    private List<String> resolvePointIds(UserWeaknessMemory memory) {
        if (StringUtils.hasText(memory.getAnchorPointId())) {
            return List.of(memory.getAnchorPointId().trim());
        }
        String habitKey = memory.getAnchorHabitKey();
        if (StringUtils.hasText(habitKey) && habitKey.startsWith("FAM_")) {
            return KnowledgePointStatsSupport.expandFamilyIds(pointDictionary, List.of(habitKey.trim()));
        }
        if (StringUtils.hasText(habitKey)) {
            return List.of(habitKey.trim());
        }
        return List.of();
    }

    public record EvidenceLine(String originalSentence, String targetSentence) {
        public static EvidenceLine empty() {
            return new EvidenceLine(null, null);
        }

        public boolean isPresent() {
            return StringUtils.hasText(originalSentence) && StringUtils.hasText(targetSentence);
        }
    }
}
