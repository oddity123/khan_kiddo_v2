package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthCard {

    private Long id;
    private String cardId;
    private Long userId;
    private String type;
    private String status;
    private LocalDate nextDueAt;
    private String front;
    private String back;
    private String sourceAnalysisId;
    private String sourceRef;
    private String evidenceJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
