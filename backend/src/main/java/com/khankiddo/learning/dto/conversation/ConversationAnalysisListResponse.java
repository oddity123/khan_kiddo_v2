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
        /** 综合口语自然度（来自 educational_summary JSON） */
        private Integer performanceScore;
        /** 四维度分项得分（来自 educational_summary JSON） */
        private PerformanceDimensionScoresDto dimensionScores;
    }
}
