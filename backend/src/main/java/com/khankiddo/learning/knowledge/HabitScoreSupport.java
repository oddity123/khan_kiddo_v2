package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 习惯打分输入装配：DB 行 / 现场 grammar → {@link HabitScoreInput.ErrorHit} 单点。
 * 中文表达不参与习惯竞争。
 */
public final class HabitScoreSupport {

    private HabitScoreSupport() {
    }

    /** 字典严重度；无 pointId 时返回 {@code null}（打分器可再兜底）。 */
    public static String resolveErrorLevel(String pointId, PointDictionary pointDictionary) {
        if (!StringUtils.hasText(pointId) || pointDictionary == null) {
            return null;
        }
        return PointScoringSupport.errorLevel(pointDictionary.resolveOrFallback(pointId));
    }

    public static boolean hasAnyPointId(List<ConversationAnalysisItem> rows) {
        return !CollectionUtils.isEmpty(rows)
                && rows.stream().anyMatch(row -> StringUtils.hasText(row.getPointId()));
    }

    /**
     * 由持久化错误行组装命中列表（含空 pointId 行，与历史 Service 行为一致）。
     */
    public static List<HabitScoreInput.ErrorHit> errorHitsFromItems(
            List<ConversationAnalysisItem> rows,
            PointDictionary pointDictionary) {
        if (CollectionUtils.isEmpty(rows)) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new HabitScoreInput.ErrorHit(
                        row.getPointId(),
                        row.getSentenceId() != null ? String.valueOf(row.getSentenceId()) : null,
                        row.getOriginalSentence(),
                        row.getErrorPoint(),
                        row.getSuggestion(),
                        resolveErrorLevel(row.getPointId(), pointDictionary)))
                .toList();
    }

    /**
     * 由 Stage2 grammar 结果组装命中；{@code errorLevel} 取字典原始字段（与 Pipeline 历史口径一致）。
     */
    public static List<HabitScoreInput.ErrorHit> errorHitsFromGrammar(
            GrammarAnalysisResult grammar,
            PointDictionary pointDictionary) {
        if (grammar == null || CollectionUtils.isEmpty(grammar.getItems()) || pointDictionary == null) {
            return List.of();
        }
        List<HabitScoreInput.ErrorHit> hits = new ArrayList<>();
        long sentenceId = 1;
        for (GrammarSentenceItemDto item : grammar.getItems()) {
            if (!CollectionUtils.isEmpty(item.getErrors())) {
                for (GrammarErrorDto error : item.getErrors()) {
                    PointDefinition point = pointDictionary.resolveOrFallback(error.getPointId());
                    hits.add(new HabitScoreInput.ErrorHit(
                            point.pointId(),
                            String.valueOf(sentenceId),
                            item.getOriginalSentence(),
                            error.getPoint(),
                            item.getSuggestion(),
                            point.errorLevel()));
                }
            }
            sentenceId++;
        }
        return hits;
    }
}
