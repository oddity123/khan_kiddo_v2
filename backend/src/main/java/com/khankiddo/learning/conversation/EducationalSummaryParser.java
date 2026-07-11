package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.conversation.scoring.PerformanceScoreResult;
import com.khankiddo.learning.conversation.scoring.PerformanceScorer;
import com.khankiddo.learning.conversation.scoring.PerformanceScoringInput;
import com.khankiddo.learning.dto.conversation.*;
import com.khankiddo.learning.model.enums.ProblemType;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class EducationalSummaryParser {

    private static final int MAIN_CATEGORY_MAX_LEN = 24;
    private static final int MAIN_CATEGORY_TOP_N = 2;
    private static final Pattern MARKDOWN_SUMMARY_SECTION = Pattern.compile(
            "(?m)^#{2,3}\\s*整体总结\\s*\\r?\\n([\\s\\S]*)",
            Pattern.MULTILINE);

    private final ObjectMapper objectMapper;
    private final PerformanceScorer performanceScorer;

    public EducationalSummaryParser(ObjectMapper objectMapper, PerformanceScorer performanceScorer) {
        this.objectMapper = objectMapper;
        this.performanceScorer = performanceScorer;
    }

    public EducationalSummaryDto parseMarkdownSummary(
            String markdown,
            GrammarAnalysisResult grammar,
            int userSentenceCount,
            int englishPracticeCount,
            int chineseExpressionCount) {
        int totalIssues = countIssues(grammar);
        if (!StringUtils.hasText(markdown)) {
            return buildReport(grammar, totalIssues, userSentenceCount, englishPracticeCount,
                    chineseExpressionCount, "无", "本次扫描未发现句子级错误。");
        }
        String trimmed = markdown.trim();
        String mainCategory = computeMainCategory(grammar);
        String levelSummary = extractSection(trimmed, MARKDOWN_SUMMARY_SECTION);
        if (!StringUtils.hasText(levelSummary)) {
            levelSummary = "请以句子级分析为准。";
        }
        return buildReport(grammar, totalIssues, userSentenceCount, englishPracticeCount,
                chineseExpressionCount, mainCategory, levelSummary.trim());
    }

    public EducationalSummaryDto defaultReport(
            GrammarAnalysisResult grammar,
            int userSentenceCount,
            int englishPracticeCount,
            int chineseExpressionCount) {
        int totalIssues = countIssues(grammar);
        if (totalIssues == 0) {
            return buildReport(grammar, 0, userSentenceCount, englishPracticeCount, chineseExpressionCount,
                    "无", "本次扫描未发现句子级错误，表达与输入一致。");
        }
        return buildReport(grammar, totalIssues, userSentenceCount, englishPracticeCount, chineseExpressionCount,
                computeMainCategory(grammar), "会话概要生成中断，请以句子级分析为准。");
    }

    /**
     * 从 {@code educational_summary} JSON 读取已持久化的分项得分（列表/概览用）。
     */
    public EducationalSummaryStatsDto readPersistedStats(String educationalSummaryJson) {
        EducationalSummaryDto root = fromJson(educationalSummaryJson);
        return hasPersistedScores(root) ? root.getReport().getOverallStats() : null;
    }

    public boolean hasPersistedScores(EducationalSummaryDto summaryRoot) {
        if (ObjectUtils.isEmpty(summaryRoot) || ObjectUtils.isEmpty(summaryRoot.getReport())) {
            return false;
        }
        return hasPersistedScores(summaryRoot.getReport().getOverallStats());
    }

    private static boolean hasPersistedScores(EducationalSummaryStatsDto stats) {
        if (ObjectUtils.isEmpty(stats) || stats.getPerformanceScore() == null) {
            return false;
        }
        PerformanceDimensionScoresDto dimensions = stats.getDimensionScores();
        return ObjectUtils.isNotEmpty(dimensions)
                && dimensions.getNaturalness() != null
                && dimensions.getAccuracy() != null
                && dimensions.getFluency() != null
                && dimensions.getLexical() != null;
    }

    /**
     * 为无持久化分数的旧记录按 items 补全（新记录在分析完成时已写入 JSON，不再重算）。
     */
    public EducationalSummaryDto enrichReportWithScores(
            EducationalSummaryDto summaryRoot,
            List<AnalysisItemDto> items,
            int totalSentences) {
        if (ObjectUtils.isEmpty(summaryRoot) || ObjectUtils.isEmpty(summaryRoot.getReport())) {
            return summaryRoot;
        }
        EducationalSummaryReportDto report = summaryRoot.getReport();
        EducationalSummaryStatsDto stats = report.getOverallStats();
        if (hasPersistedScores(stats)) {
            return summaryRoot;
        }
        PerformanceScoreResult scores = performanceScorer.score(
                PerformanceScoringInput.fromAnalysisItems(items, resolveEnglishPracticeCount(stats, totalSentences)));
        EducationalSummaryStatsDto enrichedStats = mergeScores(
                stats,
                totalSentences,
                scores);
        return EducationalSummaryDto.builder()
                .report(EducationalSummaryReportDto.builder()
                        .overallStats(enrichedStats)
                        .overallSummary(report.getOverallSummary())
                        .build())
                .chineseExpressions(summaryRoot.getChineseExpressions())
                .build();
    }

    private static int resolveEnglishPracticeCount(EducationalSummaryStatsDto stats, int fallbackTotalSentences) {
        if (ObjectUtils.isEmpty(stats) || stats.getTotalSentences() == null) {
            return Math.max(1, fallbackTotalSentences);
        }
        int total = stats.getTotalSentences();
        int chinese = ObjectUtils.defaultIfNull(stats.getChineseExpressionCount(), 0);
        return Math.max(1, total - chinese);
    }

    public String toJson(EducationalSummaryDto summaryRoot) {
        try {
            return objectMapper.writeValueAsString(summaryRoot);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化教育总结失败", ex);
        }
    }

    public EducationalSummaryDto fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return EducationalSummaryDto.builder().build();
        }
        try {
            return objectMapper.readValue(json, EducationalSummaryDto.class);
        } catch (Exception ex) {
            return EducationalSummaryDto.builder().build();
        }
    }

    private EducationalSummaryDto buildReport(
            GrammarAnalysisResult grammar,
            int totalIssues,
            int userSentenceCount,
            int englishPracticeCount,
            int chineseExpressionCount,
            String mainCategory,
            String levelSummary) {
        PerformanceScoreResult scores = performanceScorer.score(
                PerformanceScoringInput.fromGrammar(grammar, englishPracticeCount));
        EducationalSummaryStatsDto overallStats = EducationalSummaryStatsDto.builder()
                .totalIssues(totalIssues)
                .totalSentences(userSentenceCount)
                .chineseExpressionCount(chineseExpressionCount)
                .mainCategory(mainCategory)
                .performanceScore(scores.overall())
                .dimensionScores(scores.toDimensionScoresDto())
                .build();
        EducationalSummaryReportDto report = EducationalSummaryReportDto.builder()
                .overallStats(overallStats)
                .overallSummary(EducationalSummaryOverallDto.builder()
                        .levelSummary(levelSummary)
                        .build())
                .build();
        return EducationalSummaryDto.builder().report(report).build();
    }

    private static EducationalSummaryStatsDto mergeScores(
            EducationalSummaryStatsDto existing,
            int totalSentences,
            PerformanceScoreResult scores) {
        EducationalSummaryStatsDto.EducationalSummaryStatsDtoBuilder builder = EducationalSummaryStatsDto.builder();
        if (ObjectUtils.isNotEmpty(existing)) {
            builder.totalIssues(existing.getTotalIssues())
                    .totalSentences(existing.getTotalSentences())
                    .mainCategory(existing.getMainCategory());
        }
        if (ObjectUtils.isEmpty(existing) || existing.getTotalSentences() == null) {
            builder.totalSentences(totalSentences);
        }
        return builder
                .performanceScore(scores.overall())
                .dimensionScores(scores.toDimensionScoresDto())
                .build();
    }

    private int countIssues(GrammarAnalysisResult grammar) {
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems())) {
            return 0;
        }
        return grammar.getItems().stream()
                .mapToInt(item -> CollectionUtils.isEmpty(item.getErrors()) ? 0 : item.getErrors().size())
                .sum();
    }

    /**
     * 由实际错误分布确定性地推导「主要挑战」：取出现频次最高的前 {@value #MAIN_CATEGORY_TOP_N}
     * 类错误，按频次降序（同频按名称排序保证稳定），用「、」连接。保证与 errorTypeDistribution 一致。
     */
    private String computeMainCategory(GrammarAnalysisResult grammar) {
        Map<String, Integer> counts = new HashMap<>();
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems())) {
            return "无";
        }
        for (GrammarSentenceItemDto item : grammar.getItems()) {
            if (CollectionUtils.isEmpty(item.getErrors())) {
                continue;
            }
            for (GrammarErrorDto error : item.getErrors()) {
                if (!StringUtils.hasText(error.getType())) {
                    continue;
                }
                String label = ProblemType.translate(error.getType());
                counts.merge(label, 1, Integer::sum);
            }
        }
        String joined = counts.entrySet().stream()
                .sorted((a, b) -> {
                    int byCount = Integer.compare(b.getValue(), a.getValue());
                    return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
                })
                .limit(MAIN_CATEGORY_TOP_N)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("、"));
        if (!StringUtils.hasText(joined)) {
            return "无";
        }
        return joined.length() > MAIN_CATEGORY_MAX_LEN ? joined.substring(0, MAIN_CATEGORY_MAX_LEN) : joined;
    }

    private static String extractSection(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String content = matcher.group(1);
        return StringUtils.hasText(content) ? content.trim() : null;
    }

    public String formatItemsForSummary(GrammarAnalysisResult grammar) {
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems())) {
            return "无错误。";
        }
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (GrammarSentenceItemDto item : grammar.getItems()) {
            if (CollectionUtils.isEmpty(item.getErrors())) {
                continue;
            }
            String types = item.getErrors().stream()
                    .filter(error -> StringUtils.hasText(error.getType()))
                    .map(error -> ProblemType.translate(error.getType()))
                    .collect(Collectors.joining("、"));
            if (StringUtils.hasText(types)) {
                lines.add(index++ + ". " + types);
            }
        }
        return lines.isEmpty() ? "无错误。" : String.join("\n", lines);
    }
}
