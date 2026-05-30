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
public class ConversationAnalysisItem {

    private Long id;
    private String analysisId;
    private Long sentenceId;
    private String originalSentence;
    private String problemTypes;
    private String errorPoint;
    private String suggestion;
    private LocalDateTime createdAt;
}
