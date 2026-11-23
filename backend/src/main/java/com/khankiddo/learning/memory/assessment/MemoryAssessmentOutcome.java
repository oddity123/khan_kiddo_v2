package com.khankiddo.learning.memory.assessment;

import org.springframework.util.StringUtils;

/**
 * 单目标回检评估结果枚举。
 */
public enum MemoryAssessmentOutcome {
    TRANSFER_SUCCESS,
    PROMPTED_CORRECT,
    PRACTICE_ERROR,
    NO_OPPORTUNITY;

    public static MemoryAssessmentOutcome fromStringOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return MemoryAssessmentOutcome.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
