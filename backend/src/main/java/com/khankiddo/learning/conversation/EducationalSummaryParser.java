package com.khankiddo.learning.conversation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.model.enums.ProblemType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class EducationalSummaryParser {

    private static final int MAIN_CATEGORY_MAX_LEN = 24;
    private static final Pattern MARKDOWN_MAIN_SECTION = Pattern.compile(
            "(?m)^#{2,3}\\s*主要挑战\\s*\\r?\\n([\\s\\S]*?)(?=^#{2,3}\\s*整体总结\\s*$|$)",
            Pattern.MULTILINE);
    private static final Pattern MARKDOWN_SUMMARY_SECTION = Pattern.compile(
            "(?m)^#{2,3}\\s*整体总结\\s*\\r?\\n([\\s\\S]*)",
            Pattern.MULTILINE);

    private final ObjectMapper objectMapper;

    public EducationalSummaryParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parseMarkdownSummary(String markdown, GrammarAnalysisResult grammar, int userSentenceCount) {
        int totalIssues = countIssues(grammar);
        if (!StringUtils.hasText(markdown)) {
            return buildReport(totalIssues, userSentenceCount, "无", "本次扫描未发现句子级错误。");
        }
        String trimmed = markdown.trim();
        String mainCategory = sanitizeMainCategory(extractSection(trimmed, MARKDOWN_MAIN_SECTION));
        String levelSummary = extractSection(trimmed, MARKDOWN_SUMMARY_SECTION);
        if (!StringUtils.hasText(mainCategory)) {
            mainCategory = computeMainCategory(grammar);
        }
        if (!StringUtils.hasText(levelSummary)) {
            levelSummary = "请以句子级分析为准。";
        }
        return buildReport(totalIssues, userSentenceCount, mainCategory, levelSummary.trim());
    }

    public Map<String, Object> defaultReport(GrammarAnalysisResult grammar, int userSentenceCount) {
        int totalIssues = countIssues(grammar);
        if (totalIssues == 0) {
            return buildReport(0, userSentenceCount, "无", "本次扫描未发现句子级错误，表达与输入一致。");
        }
        return buildReport(totalIssues, userSentenceCount, computeMainCategory(grammar),
                "会话概要生成中断，请以句子级分析为准。");
    }

    public String toJson(Map<String, Object> reportRoot) {
        try {
            return objectMapper.writeValueAsString(reportRoot);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化教育总结失败", ex);
        }
    }

    public Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> buildReport(int totalIssues, int sentenceCount, String mainCategory, String levelSummary) {
        Map<String, Object> overallStats = new LinkedHashMap<>();
        overallStats.put("totalIssues", totalIssues);
        overallStats.put("totalSentences", sentenceCount);
        overallStats.put("mainCategory", mainCategory);

        Map<String, Object> overallSummary = Map.of("levelSummary", levelSummary);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("overallStats", overallStats);
        report.put("overallSummary", overallSummary);
        return Map.of("report", report);
    }

    private int countIssues(GrammarAnalysisResult grammar) {
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems())) {
            return 0;
        }
        return grammar.getItems().stream()
                .mapToInt(item -> CollectionUtils.isEmpty(item.getErrors()) ? 0 : item.getErrors().size())
                .sum();
    }

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
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("未知");
    }

    private static String extractSection(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String content = matcher.group(1);
        return StringUtils.hasText(content) ? content.trim() : null;
    }

    private static String sanitizeMainCategory(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未知";
        }
        String line = raw.lines().findFirst().orElse(raw).trim();
        if (line.length() > MAIN_CATEGORY_MAX_LEN) {
            return line.substring(0, MAIN_CATEGORY_MAX_LEN);
        }
        return line;
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
