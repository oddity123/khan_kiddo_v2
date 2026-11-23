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
public class PracticeAttemptResponse {

    private Long id;
    private Integer attemptNo;
    private String userResponse;
    private String expectedResponse;
    private Boolean isCorrect;
    private Boolean independent;
    private Boolean hintUsed;
    private BigDecimal score;
    private String feedback;
    private LocalDateTime createdAt;
}
