package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.AnalysisDashboardStats;
import com.khankiddo.learning.dto.DailyPracticeStat;
import com.khankiddo.learning.dto.GrowthCardStatusStat;
import com.khankiddo.learning.dto.HomePageResponse;
import com.khankiddo.learning.dto.ProblemTypeStat;
import com.khankiddo.learning.dto.RecentSentenceView;
import com.khankiddo.learning.dto.WeeklyDeltaStat;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.GrowthCardMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.DailyCount;
import com.khankiddo.learning.model.GrowthCardStatusCount;
import com.khankiddo.learning.model.ProblemTypeCount;
import com.khankiddo.learning.model.enums.ErrorLevel;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final String DEFAULT_TITLE = "Khan Kiddo 英语学习助手";
    private static final String DEFAULT_DESCRIPTION = "让 AI 对话练习真正变得有效";
    private static final int DAILY_TREND_DAYS = 14;
    private static final int ISSUE_HEATMAP_DAYS = 30;
    private static final int PROBLEM_DISTRIBUTION_LIMIT = 5;
    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("M/d");

    private final ConversationAnalysisItemMapper conversationAnalysisItemMapper;
    private final ConversationAnalysisMapper conversationAnalysisMapper;
    private final GrowthCardMapper growthCardMapper;

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
        List<String> seriousProblemTypes = Arrays.stream(ProblemType.values())
                .filter(pt -> pt.getErrorLevel() == ErrorLevel.FATAL || pt.getErrorLevel() == ErrorLevel.BASIC)
                .map(ProblemType::getEnglishName)
                .collect(Collectors.toList());
        long seriousIssueCount = conversationAnalysisItemMapper.countByUserIdAndProblemTypes(userId, seriousProblemTypes);

        Map<String, Object> topType = conversationAnalysisItemMapper.getMostCommonProblemTypeByUserId(userId);
        String mostCommonErrorType = "—";
        if (topType != null && topType.get("problemType") != null) {
            mostCommonErrorType = ProblemType.translate(String.valueOf(topType.get("problemType")));
        }

        long recent7DaysSentenceCount =
                conversationAnalysisItemMapper.countDistinctSentencesInLast7DaysByUserId(userId);
        long analysisCount = conversationAnalysisMapper.countByUserIdAndStatusAndDays(userId, "success", null);
        long dueGrowthCardCount = growthCardMapper.countDueByUserId(userId, LocalDate.now());
        long previous7DaysSentenceCount =
                conversationAnalysisItemMapper.countDistinctSentencesBetweenDaysAgo(userId, 14, 7);
        List<ProblemTypeCount> recent30DaysProblemRows = conversationAnalysisItemMapper
                .countProblemTypesByUserIdAndDays(userId, 30);
        List<ProblemTypeCount> recent7DaysProblemRows = conversationAnalysisItemMapper
                .countProblemTypesByUserIdAndDays(userId, 7);
        List<ProblemTypeStat> recent30DaysProblemTypes = recent30DaysProblemRows.stream()
                .map(this::toProblemTypeStat)
                .collect(Collectors.toList());
        List<ProblemTypeStat> recent7DaysProblemTypes = recent7DaysProblemRows.stream()
                .map(this::toProblemTypeStat)
                .collect(Collectors.toList());
        List<ConversationAnalysisItem> rawRecent =
                conversationAnalysisItemMapper.findRecentSentencesByUserId(userId, 3);

        return AnalysisDashboardStats.builder()
                .analyzedSentenceCount(analyzedSentenceCount)
                .seriousIssueCount(seriousIssueCount)
                .mostCommonErrorType(mostCommonErrorType)
                .recent7DaysSentenceCount(recent7DaysSentenceCount)
                .analysisCount(analysisCount)
                .dueGrowthCardCount(dueGrowthCardCount)
                .recent30DaysTopProblemTypes(recent30DaysProblemTypes.stream().limit(3).collect(Collectors.toList()))
                .recent7DaysProblemTypeDistribution(buildProblemTypeDistribution(recent7DaysProblemTypes))
                .dailyPracticeTrend(buildDailyPracticeTrend(userId))
                .dailyIssueHeatmap(buildDailyIssueHeatmap(userId))
                .growthCardStatusCounts(buildGrowthCardStatusCounts(userId, dueGrowthCardCount))
                .weeklySentenceDelta(buildWeeklyDelta(recent7DaysSentenceCount, previous7DaysSentenceCount))
                .recentSentences(buildRecentSentenceViews(rawRecent))
                .build();
    }

    private ProblemTypeStat toProblemTypeStat(ProblemTypeCount row) {
        return ProblemTypeStat.builder()
                .label(ProblemType.translate(row.getProblemType()))
                .count(row.getCount() == null ? 0L : row.getCount())
                .build();
    }

    private List<ProblemTypeStat> buildProblemTypeDistribution(List<ProblemTypeStat> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        List<ProblemTypeStat> visible = new ArrayList<>(rows.stream()
                .limit(PROBLEM_DISTRIBUTION_LIMIT)
                .toList());
        long otherCount = rows.stream()
                .skip(PROBLEM_DISTRIBUTION_LIMIT)
                .mapToLong(ProblemTypeStat::getCount)
                .sum();
        if (otherCount > 0) {
            visible.add(ProblemTypeStat.builder()
                    .label("其他")
                    .count(otherCount)
                    .build());
        }
        return visible;
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
            return Collections.emptyList();
        }
        List<RecentSentenceView> result = new ArrayList<>();
        for (ConversationAnalysisItem first : rawRecent) {
            List<ConversationAnalysisItem> allForSentence = conversationAnalysisItemMapper
                    .findByAnalysisIdAndSentenceId(first.getAnalysisId(), first.getSentenceId());
            List<String> tags = allForSentence.stream()
                    .map(ConversationAnalysisItem::getProblemTypes)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .map(ProblemType::translate)
                    .collect(Collectors.toList());
            result.add(RecentSentenceView.builder()
                    .originalSentence(first.getOriginalSentence())
                    .suggestion(first.getSuggestion())
                    .problemTypeTags(tags)
                    .createdAt(first.getCreatedAt())
                    .build());
        }
        return result;
    }
}
