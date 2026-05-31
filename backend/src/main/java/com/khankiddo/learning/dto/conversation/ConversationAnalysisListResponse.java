package com.khankiddo.learning.dto.conversation;

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
public class ConversationAnalysisListResponse {

    private long total;
    private List<SummaryRow> records;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryRow {
        private String analysisId;
        private String status;
        private Long processingTimeMs;
        private LocalDateTime createdAt;
        private String preview;
    }
}
