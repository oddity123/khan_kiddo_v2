package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.model.GrowthCardEvidence;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将行动卡 / 中文表达证据去重后建成待落库行（按 track_key 卡内唯一）。
 */
public final class GrowthCardEvidenceSupport {

    private GrowthCardEvidenceSupport() {
    }

    public static List<GrowthCardEvidence> fromHabitExamples(
            long userId,
            String cardId,
            String analysisId,
            ActionCardDto habit) {
        if (habit == null || CollectionUtils.isEmpty(habit.getExamples())) {
            return List.of();
        }
        Map<String, GrowthCardEvidence> byKey = new LinkedHashMap<>();
        int order = 0;
        for (ActionCardDto.ExampleDto example : habit.getExamples()) {
            if (example == null || !StringUtils.hasText(example.getOriginalSentence())) {
                continue;
            }
            String original = example.getOriginalSentence().trim();
            String sentenceId = StringUtils.hasText(example.getSentenceId())
                    ? example.getSentenceId().trim()
                    : null;
            String trackKey = trackKey(sentenceId, original);
            if (byKey.containsKey(trackKey)) {
                continue;
            }
            byKey.put(trackKey, GrowthCardEvidence.builder()
                    .cardId(cardId)
                    .userId(userId)
                    .sourceAnalysisId(analysisId)
                    .sentenceId(sentenceId)
                    .trackKey(trackKey)
                    .originalSentence(original)
                    .suggestion(trimToNull(example.getSuggestion()))
                    .sortOrder(order++)
                    .build());
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<GrowthCardEvidence> fromChineseExpression(
            long userId,
            String cardId,
            String analysisId,
            ChineseExpressionDto expression) {
        if (expression == null || !StringUtils.hasText(expression.getOriginalSentence())) {
            return List.of();
        }
        String original = expression.getOriginalSentence().trim();
        String sentenceId = expression.getOriginalIndex() != null
                ? String.valueOf(expression.getOriginalIndex())
                : null;
        return List.of(GrowthCardEvidence.builder()
                .cardId(cardId)
                .userId(userId)
                .sourceAnalysisId(analysisId)
                .sentenceId(sentenceId)
                .trackKey(trackKey(sentenceId, original))
                .originalSentence(original)
                .suggestion(trimToNull(expression.getSuggestion()))
                .sortOrder(0)
                .build());
    }

    static String trackKey(String sentenceId, String originalSentence) {
        if (StringUtils.hasText(sentenceId)) {
            return "s:" + sentenceId.trim();
        }
        return "t:" + normalizeOriginal(originalSentence);
    }

    static String normalizeOriginal(String original) {
        if (!StringUtils.hasText(original)) {
            return "";
        }
        String collapsed = original.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return collapsed.length() > 180 ? collapsed.substring(0, 180) : collapsed;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
