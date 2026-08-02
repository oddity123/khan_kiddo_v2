package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FamilyDefinition(
        String familyId,
        String titleZh,
        PointChannel channel,
        Double fixability,
        String otherPointId,
        double impactWeight,
        HabitUnit habitUnit
) {
}
