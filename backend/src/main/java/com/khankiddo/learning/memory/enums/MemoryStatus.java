package com.khankiddo.learning.memory.enums;

import org.springframework.util.StringUtils;

/**
 * 薄弱点记忆生命周期状态。
 */
public enum MemoryStatus {
    NEW,
    ACTIVE,
    PRACTICING,
    MASTERED,
    ARCHIVED;

    public static MemoryStatus fromString(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("status is required");
        }
        return MemoryStatus.valueOf(raw.trim().toUpperCase());
    }
}
