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
    private String pointDictionaryVersion;
    /**
     * 句级 ERRANT 操作批注 JSON 数组（sentenceId + tokens + edits）；未启用或失败时为 null。
     */
    private String editAnnotations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
