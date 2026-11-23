package com.khankiddo.learning.memory.enums;

import org.springframework.util.StringUtils;

/**
 * 专项练习任务状态。
 */
public enum PracticeSessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED;

    public static PracticeSessionStatus fromString(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("session status is required");
        }
        return PracticeSessionStatus.valueOf(raw.trim().toUpperCase());
    }
}
