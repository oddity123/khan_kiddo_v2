package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.model.enums.ProblemType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 评分输入：用户总句数 + 按句分组的错误类型（由 {@code pointId} 经知识点字典反查 ProblemType 枚举名）。
 */
public record PerformanceScoringInput(int totalSentences, List<SentenceErrors> sentencesWithErrors) {

    public record SentenceErrors(List<String> problemTypeKeys) {
    }

    public static PerformanceScoringInput fromGrammar(
            GrammarAnalysisResult grammar, int totalSentences, PointDictionary pointDictionary) {
        List<SentenceErrors> sentences = new ArrayList<>();
        if (grammar != null && !CollectionUtils.isEmpty(grammar.getItems())) {
            for (GrammarSentenceItemDto item : grammar.getItems()) {
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                List<String> keys = new ArrayList<>();
                for (GrammarErrorDto error : item.getErrors()) {
                    keys.add(resolveTypeKey(error.getPointId(), pointDictionary));
                }
                sentences.add(new SentenceErrors(keys));
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
                List<String> keys = new ArrayList<>();
                for (AnalysisErrorDto error : item.getErrors()) {
                    keys.add(resolveTypeKey(error.getPointId(), pointDictionary));
                }
                sentences.add(new SentenceErrors(keys));
            }
        }
        return new PerformanceScoringInput(Math.max(1, totalSentences), sentences);
    }

    private static String resolveTypeKey(String pointId, PointDictionary pointDictionary) {
        PointDefinition definition = pointDictionary.resolveOrFallback(pointId);
        String problemTypeName = definition.problemType();
        ProblemType type = ProblemType.fromEnglishName(problemTypeName);
        if (type != null) {
            return type.name();
        }
        return StringUtils.hasText(problemTypeName)
                ? problemTypeName.trim().toUpperCase().replace(' ', '_')
                : "UNKNOWN";
    }
}
