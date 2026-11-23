package com.khankiddo.learning.memory.enums;

import org.springframework.util.StringUtils;

/**
 * 专项练习形态。
 */
public enum PracticeType {
    RECALL,
    TRANSFORMATION,
    SCENARIO,
    FREE_SPEAKING,
    REVIEW;

    public static PracticeType fromStringOrDefault(String raw, PracticeType defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        return PracticeType.valueOf(raw.trim().toUpperCase());
    }
}
