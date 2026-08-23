package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 知识点字典条目。评分使用 {@code errorLevel} 与 {@code scoreProfile}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PointDefinition(
        String pointId,
        String familyId,
        PointChannel channel,
        CardKind cardKind,
        CardPolicy cardPolicy,
        HabitUnit habitUnit,
        double impactWeight,
        Double fixability,
        String errorLevel,
        String scoreProfile,
        String titleZh,
        String whyZh,
        String topTitleZh,
        String actionHintZh
) {
}
