package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 口语薄弱点长期记忆（semantic）：只存宏观模式与轨迹，不存句子或教学文案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWeaknessMemory {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private Long id;
    private Long userId;
    private String title;
    private String anchorHabitKey;
    private String anchorPointId;
    private String sourceAnalysisId;
    private Integer hitCount;
    private Integer practicedCount;
    private String status;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastPracticedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
