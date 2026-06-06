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
public class ConversationAnalysis {

    private Long id;
    private Long userId;
    private String analysisId;
    private String conversationContent;
    private String status;
    private String errorMessage;
    private Long processingTimeMs;
    private String educationalSummary;
    private String llmModelId;
    private String llmModelName;
    private String llmProvider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
