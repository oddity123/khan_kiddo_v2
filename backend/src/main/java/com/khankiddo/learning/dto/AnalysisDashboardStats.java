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
    private long analysisCount;
    private long dueGrowthCardCount;
    private List<ProblemTypeStat> recent30DaysTopProblemTypes;
    private List<ProblemTypeStat> recent7DaysProblemTypeDistribution;
    private List<DailyPracticeStat> dailyPracticeTrend;
    private List<DailyPracticeStat> dailyIssueHeatmap;
    private List<GrowthCardStatusStat> growthCardStatusCounts;
    private WeeklyDeltaStat weeklySentenceDelta;
    private List<RecentSentenceView> recentSentences;
}
