package com.khankiddo.learning.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItemResponse {

    private Long id;
    private String title;
    private String memoryType;
    private String familyId;
    private String pointId;
    private String status;
    private Integer hitCount;
    private Integer recentHitCount;
    private Integer practicedCount;
    private Integer correctCount;
    private BigDecimal masteryLevel;
    private Integer consecutiveCorrect;
    private Double priorityScore;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastPracticedAt;
    private LocalDateTime nextReviewAt;
    private String latestEvidenceOriginal;
    private String latestEvidenceCorrected;
}
