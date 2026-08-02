package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
        String problemType,
        String titleZh,
        String whyZh,
        String topTitleZh,
        String actionHintZh
) {
}
