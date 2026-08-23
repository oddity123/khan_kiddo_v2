package com.khankiddo.learning.knowledge;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 从 {@link PointDefinition} 解析评分 profile 与严重度，供口语评分与习惯卡使用。
 * 分类真源为 {@code pointId}；{@code scoreProfile} 仅用于扣分权重与维度归属。
 */
public final class PointScoringSupport {

    private static final Map<String, String> LEGACY_PROBLEM_TYPE_PROFILE = Map.ofEntries(
            Map.entry("Tense", "TENSE"),
            Map.entry("Agreement", "AGREEMENT"),
            Map.entry("Plural", "PLURAL"),
            Map.entry("Article", "ARTICLE"),
            Map.entry("Preposition", "PREPOSITION"),
            Map.entry("Pronoun", "PRONOUN"),
            Map.entry("Structure", "STRUCTURE"),
            Map.entry("Clause", "CLAUSE"),
            Map.entry("Word Form", "WORD_FORM"),
            Map.entry("Comparison", "COMPARISON"),
            Map.entry("Word Choice", "WORD_CHOICE"),
            Map.entry("Collocation", "COLLOCATION"),
            Map.entry("Chinglish", "CHINGLISH"),
            Map.entry("Redundancy", "REDUNDANCY"),
            Map.entry("Tone", "TONE"),
            Map.entry("Unnatural", "UNNATURAL"),
            Map.entry("Vocabulary", "VOCABULARY"),
            Map.entry("Formal", "FORMAL"),
            Map.entry("Incomplete", "INCOMPLETE"),
            Map.entry("Chinese", "CHINESE"));

    private PointScoringSupport() {
    }

    public static String scoreProfile(PointDefinition point) {
        if (point == null) {
            return "UNKNOWN";
        }
        if (StringUtils.hasText(point.scoreProfile())) {
            return point.scoreProfile().trim().toUpperCase(Locale.ROOT);
        }
        return profileFromProblemTypeLabel(point.problemType());
    }

    public static String errorLevel(PointDefinition point) {
        if (point == null || !StringUtils.hasText(point.errorLevel())) {
            return "STYLE";
        }
        return point.errorLevel().trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isFatal(PointDefinition point) {
        return "FATAL".equals(errorLevel(point));
    }

    public static String profileFromProblemTypeLabel(String problemTypeLabel) {
        if (!StringUtils.hasText(problemTypeLabel)) {
            return "UNKNOWN";
        }
        String trimmed = problemTypeLabel.trim();
        String mapped = LEGACY_PROBLEM_TYPE_PROFILE.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        return trimmed.toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
