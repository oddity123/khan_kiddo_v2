package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.AnalysisDashboardStats;
import com.khankiddo.learning.dto.DailyPracticeStat;
import com.khankiddo.learning.dto.GrowthCardStatusStat;
import com.khankiddo.learning.dto.HomePageResponse;
import com.khankiddo.learning.dto.KnowledgeFamilyStat;
import com.khankiddo.learning.dto.RecentSentenceView;
import com.khankiddo.learning.dto.WeeklyDeltaStat;
import com.khankiddo.learning.knowledge.KnowledgePointStatsSupport;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.GrowthCardMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.DailyCount;
import com.khankiddo.learning.model.GrowthCardStatusCount;
import com.khankiddo.learning.model.PointIdCount;
import com.khankiddo.learning.model.enums.ProblemType;
import com.khankiddo.learning.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final String DEFAULT_TITLE = "Khan Kiddo 英语学习助手";
    private static final String DEFAULT_DESCRIPTION = "让 AI 对话练习真正变得有效";
    private static final int DAILY_TREND_DAYS = 14;
    private static final int ISSUE_HEATMAP_DAYS = 30;
    private static final int FAMILY_DISTRIBUTION_LIMIT = 5;
    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("M/d");

    private final ConversationAnalysisItemMapper conversationAnalysisItemMapper;
    private final ConversationAnalysisMapper conversationAnalysisMapper;
    private final GrowthCardMapper growthCardMapper;
    private final PointDictionary pointDictionary;

    @Override
    public HomePageResponse getHomePage(Long userId) {
        boolean authenticated = ObjectUtils.isNotEmpty(userId);
        return HomePageResponse.builder()
                .title(DEFAULT_TITLE)
                .description(DEFAULT_DESCRIPTION)
                .authenticated(authenticated)
                .analysisStats(authenticated ? getAnalysisDashboardStats(userId) : null)
                .build();
    }

    private AnalysisDashboardStats getAnalysisDashboardStats(Long userId) {
        long analyzedSentenceCount = conversationAnalysisItemMapper.countDistinctSentencesByUserId(userId);
        List<String> seriousPointIds = KnowledgePointStatsSupport.seriousPointIds(pointDictionary);
        long seriousIssueCount = conversationAnalysisItemMapper.countByUserIdAndPointIds(userId, seriousPointIds);

        KnowledgeFamilyStat topFamily = resolveTopFamily(
                conversationAnalysisItemMapper.countPointIdsByUserIdAndDays(userId, null));
        String mostCommonFamilyLabel = topFamily != null ? topFamily.getLabel() : "—";

        long recent7DaysSentenceCount =
                conversationAnalysisItemMapper.countDistinctSentencesInLast7DaysByUserId(userId);
        long analysisCount = conversationAnalysisMapper.countByUserIdAndStatusAndDays(userId, "success", null);
        long dueGrowthCardCount = growthCardMapper.countDueByUserId(userId, LocalDate.now());
        long previous7DaysSentenceCount =
                conversationAnalysisItemMapper.countDistinctSentencesBetweenDaysAgo(userId, 14, 7);

        List<PointIdCount> recent7DaysPointRows = conversationAnalysisItemMapper
                .countPointIdsByUserIdAndDays(userId, 7);
        List<KnowledgeFamilyStat> recent7DaysFamilyDistribution = KnowledgePointStatsSupport
                .aggregateFamilies(recent7DaysPointRows, pointDictionary, FAMILY_DISTRIBUTION_LIMIT);

        List<ConversationAnalysisItem> rawRecent =
                conversationAnalysisItemMapper.findRecentSentencesByUserId(userId, 3);

        return AnalysisDashboardStats.builder()
                .analyzedSentenceCount(analyzedSentenceCount)
                .seriousIssueCount(seriousIssueCount)
                .mostCommonFamilyLabel(mostCommonFamilyLabel)
                .recent7DaysSentenceCount(recent7DaysSentenceCount)
                .analysisCount(analysisCount)
                .dueGrowthCardCount(dueGrowthCardCount)
                .recent7DaysFamilyDistribution(recent7DaysFamilyDistribution)
                .dailyPracticeTrend(buildDailyPracticeTrend(userId))
                .dailyIssueHeatmap(buildDailyIssueHeatmap(userId))
                .growthCardStatusCounts(buildGrowthCardStatusCounts(userId, dueGrowthCardCount))
                .weeklySentenceDelta(buildWeeklyDelta(recent7DaysSentenceCount, previous7DaysSentenceCount))
                .recentSentences(buildRecentSentenceViews(rawRecent))
                .build();
    }

    private KnowledgeFamilyStat resolveTopFamily(List<PointIdCount> rows) {
        return KnowledgePointStatsSupport.topFamily(rows, pointDictionary);
    }

    private List<DailyPracticeStat> buildDailyPracticeTrend(Long userId) {
        List<DailyCount> rows = conversationAnalysisItemMapper
                .countDailyDistinctSentencesByUserIdAndDays(userId, DAILY_TREND_DAYS);
        Map<String, Long> countByDay = new HashMap<>();
        for (DailyCount row : rows) {
            countByDay.put(row.getDay(), row.getCount() == null ? 0L : row.getCount());
        }

        LocalDate start = LocalDate.now().minusDays(DAILY_TREND_DAYS - 1L);
        List<DailyPracticeStat> trend = new ArrayList<>();
        for (int i = 0; i < DAILY_TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            String key = day.format(DATE_KEY_FORMAT);
            trend.add(DailyPracticeStat.builder()
                    .date(key)
                    .label(day.format(DATE_LABEL_FORMAT))
                    .count(countByDay.getOrDefault(key, 0L))
                    .build());
        }
        return trend;
    }

    private List<DailyPracticeStat> buildDailyIssueHeatmap(Long userId) {
        return buildDailySeries(
                conversationAnalysisItemMapper.countDailyIssuesByUserIdAndDays(userId, ISSUE_HEATMAP_DAYS),
                ISSUE_HEATMAP_DAYS);
    }

    private List<DailyPracticeStat> buildDailySeries(List<DailyCount> rows, int days) {
        Map<String, Long> countByDay = new HashMap<>();
        for (DailyCount row : rows) {
            countByDay.put(row.getDay(), row.getCount() == null ? 0L : row.getCount());
        }

        LocalDate start = LocalDate.now().minusDays(days - 1L);
        List<DailyPracticeStat> series = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            String key = day.format(DATE_KEY_FORMAT);
            series.add(DailyPracticeStat.builder()
                    .date(key)
                    .label(day.format(DATE_LABEL_FORMAT))
                    .count(countByDay.getOrDefault(key, 0L))
                    .build());
        }
        return series;
    }

    private List<GrowthCardStatusStat> buildGrowthCardStatusCounts(Long userId, long dueGrowthCardCount) {
        Map<String, Long> countByStatus = growthCardMapper.countStatusByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        GrowthCardStatusCount::getStatus,
                        row -> row.getCount() == null ? 0L : row.getCount(),
                        Long::sum));
        return List.of(
                GrowthCardStatusStat.builder().key("due").label("待复习").count(dueGrowthCardCount).build(),
                GrowthCardStatusStat.builder().key("unfamiliar").label("未熟").count(countByStatus.getOrDefault("unfamiliar", 0L)).build(),
                GrowthCardStatusStat.builder().key("fuzzy").label("模糊").count(countByStatus.getOrDefault("fuzzy", 0L)).build(),
                GrowthCardStatusStat.builder().key("mastered").label("已掌握").count(countByStatus.getOrDefault("mastered", 0L)).build()
        );
    }

    private WeeklyDeltaStat buildWeeklyDelta(long current, long previous) {
        long delta = current - previous;
        int percent;
        if (previous == 0) {
            percent = current > 0 ? 100 : 0;
        } else {
            percent = (int) Math.round(delta * 100.0 / previous);
        }
        return WeeklyDeltaStat.builder()
                .current(current)
                .previous(previous)
                .delta(delta)
                .percent(percent)
                .build();
    }

    private List<RecentSentenceView> buildRecentSentenceViews(List<ConversationAnalysisItem> rawRecent) {
        if (CollectionUtils.isEmpty(rawRecent)) {
            return List.of();
        }
        List<RecentSentenceView> result = new ArrayList<>();
        for (ConversationAnalysisItem first : rawRecent) {
            List<ConversationAnalysisItem> allForSentence = conversationAnalysisItemMapper
                    .findByAnalysisIdAndSentenceId(first.getAnalysisId(), first.getSentenceId());
            Set<String> familyLabels = new LinkedHashSet<>();
            for (ConversationAnalysisItem row : allForSentence) {
                if (StringUtils.hasText(row.getPointId())) {
                    PointDefinition definition = pointDictionary.resolveOrFallback(row.getPointId());
                    familyLabels.add(KnowledgePointStatsSupport.familyTitle(pointDictionary, definition.familyId()));
                } else if (StringUtils.hasText(row.getProblemTypes())) {
                    familyLabels.add(ProblemType.translate(row.getProblemTypes()));
                }
            }
            result.add(RecentSentenceView.builder()
                    .originalSentence(first.getOriginalSentence())
                    .suggestion(first.getSuggestion())
                    .familyTags(new ArrayList<>(familyLabels))
                    .createdAt(first.getCreatedAt())
                    .build());
        }
        return result;
    }
}
