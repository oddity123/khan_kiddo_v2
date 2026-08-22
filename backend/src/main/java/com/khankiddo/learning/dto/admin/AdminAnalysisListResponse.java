package com.khankiddo.learning.dto.admin;

import com.khankiddo.learning.dto.conversation.PerformanceDimensionScoresDto;
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
public class AdminAnalysisListResponse {

    private long total;
    private List<SummaryRow> records;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryRow {
        private String analysisId;
        private Long userId;
        private String username;
        private String status;
        private Long processingTimeMs;
        private LocalDateTime createdAt;
        private String preview;
        private Integer contentCharCount;
        private String llmModelId;
        private String llmModelName;
        private String llmProvider;
        private Integer performanceScore;
        private PerformanceDimensionScoresDto dimensionScores;
    }
}
