package com.khankiddo.learning.analysistask;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAnalysisTask {

    private Long id;
    private String taskId;
    private Long userId;
    private String status;
    private String conversationContent;
    private String contentFingerprint;
    private String sourceType;
    private String sourceConversationId;
    private String modelId;
    private String analysisId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
}
