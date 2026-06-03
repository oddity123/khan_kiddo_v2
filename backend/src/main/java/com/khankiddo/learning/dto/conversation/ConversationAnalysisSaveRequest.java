package com.khankiddo.learning.dto.conversation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAnalysisSaveRequest {

    @NotBlank(message = "对话内容不能为空")
    private String conversationContent;

    @Valid
    private List<SaveAnalysisItem> items;

    private String analysisId;
    private LocalDateTime analyzedAt;
    private Long processingTimeMs;
    private String educationalSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveAnalysisItem {

        @NotBlank(message = "原句不能为空")
        private String originalSentence;

        private String suggestion;

        @Valid
        private List<SaveError> errors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveError {

        @NotBlank(message = "问题类型不能为空")
        private String type;

        private String point;
    }
}
