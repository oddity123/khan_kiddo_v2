package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAnalysisResultDto {

    private String analysisId;
    private LocalDateTime analyzedAt;
    private Long processingTimeMs;
    private String status;
    private Map<String, Object> analysisResults;
    private String educationalSummaryJson;

    private String llmModelId;
    private String llmModelName;
    private String llmProvider;
}
