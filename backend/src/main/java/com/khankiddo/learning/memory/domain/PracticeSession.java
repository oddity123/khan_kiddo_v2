package com.khankiddo.learning.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 专项练习任务记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeSession {

    private Long id;
    private Long userId;
    private Long memoryId;
    private String practiceType;
    private String targetPointId;
    private String status;
    private String sourceConversationId;
    private String analysisId;
    private Integer totalAttempts;
    private Integer correctAttempts;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
