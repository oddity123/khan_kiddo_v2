package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.dto.conversation.AnalysisErrorDto;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.model.enums.ProblemType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 评分输入：用户总句数 + 按句分组的错误类型（英文枚举名或中文展示名均可解析）。
 */
public record PerformanceScoringInput(int totalSentences, List<SentenceErrors> sentencesWithErrors) {

    public record SentenceErrors(List<String> problemTypeKeys) {
    }

    public static PerformanceScoringInput fromGrammar(GrammarAnalysisResult grammar, int totalSentences) {
        List<SentenceErrors> sentences = new ArrayList<>();
        if (grammar != null && !CollectionUtils.isEmpty(grammar.getItems())) {
            for (GrammarSentenceItemDto item : grammar.getItems()) {
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                List<String> keys = new ArrayList<>();
                for (GrammarErrorDto error : item.getErrors()) {
                    keys.add(resolveTypeKey(error.getType()));
                }
                sentences.add(new SentenceErrors(keys));
            }
        }
        return new PerformanceScoringInput(Math.max(1, totalSentences), sentences);
    }

    public static PerformanceScoringInput fromAnalysisItems(List<AnalysisItemDto> items, int totalSentences) {
        List<SentenceErrors> sentences = new ArrayList<>();
        if (!CollectionUtils.isEmpty(items)) {
            for (AnalysisItemDto item : items) {
                if (CollectionUtils.isEmpty(item.getErrors())) {
                    continue;
                }
                List<String> keys = new ArrayList<>();
                for (AnalysisErrorDto error : item.getErrors()) {
                    keys.add(resolveTypeKey(error.getType()));
                }
                sentences.add(new SentenceErrors(keys));
            }
        }
        return new PerformanceScoringInput(Math.max(1, totalSentences), sentences);
    }

    private static String resolveTypeKey(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "UNKNOWN";
        }
        String trimmed = rawType.trim();
        ProblemType byEnglish = ProblemType.fromEnglishName(trimmed);
        if (byEnglish != null) {
            return byEnglish.name();
        }
        for (ProblemType type : ProblemType.values()) {
            if (type.getChineseName().equals(trimmed)) {
                return type.name();
            }
        }
        return trimmed.toUpperCase().replace(' ', '_');
    }
}
