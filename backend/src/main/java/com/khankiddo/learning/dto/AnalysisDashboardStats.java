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
    private String mostCommonFamilyLabel;
    private long recent7DaysSentenceCount;
    private long analysisCount;
    private long dueGrowthCardCount;
    private List<KnowledgeFamilyStat> recent7DaysFamilyDistribution;
    private List<DailyPracticeStat> dailyPracticeTrend;
    private List<DailyPracticeStat> dailyIssueHeatmap;
    private List<GrowthCardStatusStat> growthCardStatusCounts;
    private WeeklyDeltaStat weeklySentenceDelta;
    private List<RecentSentenceView> recentSentences;
}
