package com.khankiddo.learning.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专项练习单次回答及评估记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeAttempt {

    private Long id;
    private Long sessionId;
    private Integer attemptNo;
    private String userResponse;
    private String expectedResponse;
    private Boolean isCorrect;
    private Boolean independent;
    private Boolean hintUsed;
    private BigDecimal score;
    private String feedback;
    private String evaluationJson;
    private LocalDateTime createdAt;
}
