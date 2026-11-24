package com.khankiddo.learning.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 口语薄弱点长期记忆主表实体：按 (userId, pointId) 唯一聚合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeaknessMemory {

    private Long id;
    private Long userId;
    private String title;
    private String memoryType;
    private String familyId;
    private String pointId;
    private String status;
    private Integer hitCount;
    private Integer recentHitCount;
    /** 累计发起的专项练习会话数（以 practice_session 行数为唯一真源）。 */
    private Integer practicedCount;
    private Integer correctCount;
    private BigDecimal masteryLevel;
    private Integer consecutiveCorrect;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastPracticedAt;
    private LocalDateTime nextReviewAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
