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
public class ConversationAnalysisDetailDto {

    private String analysisId;
    private String conversationContent;
    private String status;
    private Long processingTimeMs;
    private LocalDateTime createdAt;
    private MapHolder educationalSummary;
    private List<AnalysisItemDto> items;
    private List<ErrorTypeDistributionDto> errorTypeDistribution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapHolder {
        private Object report;
    }
}
