package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.knowledge.PointDictionary;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 评分输入：用户总句数 + 按句分组的合法 {@code pointId}（经字典校验或兜底）。
 */
public record PerformanceScoringInput(int totalSentences, List<SentenceErrors> sentencesWithErrors) {

    public record SentenceErrors(List<String> pointIds) {
    }

    public static PerformanceScoringInput fromGrammar(
            GrammarAnalysisResult grammar, int totalSentences, PointDictionary pointDictionary) {
        List<SentenceErrors> sentences = new ArrayList<>();
        if (grammar != null && !CollectionUtils.isEmpty(grammar.getItems())) {
            for (GrammarSentenceItemDto item : grammar.getItems()) {
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                List<String> pointIds = new ArrayList<>();
                for (GrammarErrorDto error : item.getErrors()) {
                    pointIds.add(requirePersistedPointId(error.getPointId(), pointDictionary));
                }
                sentences.add(new SentenceErrors(pointIds));
            }
        }
        return new PerformanceScoringInput(Math.max(1, totalSentences), sentences);
    }

    public static PerformanceScoringInput fromAnalysisItems(
            List<AnalysisItemDto> items, int totalSentences, PointDictionary pointDictionary) {
        List<SentenceErrors> sentences = new ArrayList<>();
        if (!CollectionUtils.isEmpty(items)) {
            for (AnalysisItemDto item : items) {
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                List<String> pointIds = new ArrayList<>();
                for (AnalysisErrorDto error : item.getErrors()) {
                    pointIds.add(requirePersistedPointId(error.getPointId(), pointDictionary));
                }
                sentences.add(new SentenceErrors(pointIds));
            }
        }
        return new PerformanceScoringInput(Math.max(1, totalSentences), sentences);
    }

    private static String requirePersistedPointId(String pointId, PointDictionary pointDictionary) {
        if (!StringUtils.hasText(pointId)) {
            return pointDictionary.resolveOrFallback(null).pointId();
        }
        return pointDictionary.resolveOrFallback(pointId).pointId();
    }
}
