package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthCardEvidence {

    private Long id;
    private String cardId;
    private Long userId;
    private String sourceAnalysisId;
    private String sentenceId;
    private String trackKey;
    private String originalSentence;
    private String suggestion;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
