package com.khankiddo.learning.analysistask.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisTaskResponse {
    String taskId;
    String status;
    String analysisId;
    String errorMessage;
    String sourceType;
    String sourceConversationId;
    LocalDateTime createdAt;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
}
