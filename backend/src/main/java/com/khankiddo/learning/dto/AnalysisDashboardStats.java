package com.khankiddo.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisDashboardStats {

    private long analyzedSentenceCount;
    private long seriousIssueCount;
    private String mostCommonErrorType;
    private long recent7DaysSentenceCount;
    private List<RecentSentenceView> recentSentences;
}
