package com.khankiddo.learning.memory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薄弱点记忆证据追加记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEvidence {

    private Long id;
    private Long memoryId;
    private Long userId;
    private String analysisId;
    private String sentenceId;
    private String evidenceType;
    private String dedupKey;
    private String originalText;
    private String correctedText;
    private BigDecimal confidence;
    private LocalDateTime createdAt;
}
