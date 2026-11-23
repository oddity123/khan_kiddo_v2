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
public class MemoryEvidenceResponse {

    private Long id;
    private String analysisId;
    private String sentenceId;
    private String evidenceType;
    private String originalText;
    private String correctedText;
    private BigDecimal confidence;
    private LocalDateTime createdAt;
}
