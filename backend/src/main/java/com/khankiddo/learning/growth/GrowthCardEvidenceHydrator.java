package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.EducationalSummaryDto;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.GrowthCardEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 旧成长卡可能尚无 evidence 关系行：按 sourceAnalysisId + sourceRef 从分析回填并落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrowthCardEvidenceHydrator {

    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final EducationalSummaryParser summaryParser;
    private final GrowthCardAnalysisSupport analysisSupport;
    private final GrowthCardStore store;

    /**
     * 对缺失证据的卡尝试回填；成功写入关系表后并入返回 map。
     */
    public Map<String, List<GrowthCardEvidence>> hydrateMissing(
            List<GrowthCard> cards,
            Map<String, List<GrowthCardEvidence>> existingByCard) {
        if (CollectionUtils.isEmpty(cards)) {
            return existingByCard != null ? existingByCard : Map.of();
        }
        Map<String, List<GrowthCardEvidence>> result = new LinkedHashMap<>();
        if (existingByCard != null) {
            result.putAll(existingByCard);
        }

        Map<String, List<GrowthCard>> needByAnalysis = new LinkedHashMap<>();
        for (GrowthCard card : cards) {
            if (ObjectUtils.isEmpty(card) || !StringUtils.hasText(card.getCardId())) {
                continue;
            }
            if (!CollectionUtils.isEmpty(result.get(card.getCardId()))) {
                continue;
            }
            if (!StringUtils.hasText(card.getSourceAnalysisId()) || !StringUtils.hasText(card.getSourceRef())) {
                continue;
            }
            needByAnalysis
                    .computeIfAbsent(card.getSourceAnalysisId().trim(), ignored -> new ArrayList<>())
                    .add(card);
        }
        if (needByAnalysis.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, List<GrowthCard>> entry : needByAnalysis.entrySet()) {
            String analysisId = entry.getKey();
            List<GrowthCard> analysisCards = entry.getValue();
            try {
                hydrateAnalysisGroup(analysisId, analysisCards, result);
            } catch (Exception ex) {
                log.warn("成长卡证据回填失败 analysisId={} cards={}: {}",
                        analysisId, analysisCards.size(), ex.getMessage());
            }
        }
        return result;
    }

    private void hydrateAnalysisGroup(
            String analysisId,
            List<GrowthCard> cards,
            Map<String, List<GrowthCardEvidence>> result) {
        ConversationAnalysis analysis = analysisMapper.findByAnalysisId(analysisId).orElse(null);
        if (ObjectUtils.isEmpty(analysis)) {
            return;
        }
        EducationalSummaryDto summary = summaryParser.fromJson(analysis.getEducationalSummary());
        List<ChineseExpressionDto> expressions = summary != null && !CollectionUtils.isEmpty(summary.getChineseExpressions())
                ? summary.getChineseExpressions()
                : List.of();

        boolean needHabit = cards.stream().anyMatch(card ->
                StringUtils.hasText(card.getSourceRef()) && card.getSourceRef().startsWith("habit:"));
        Map<String, ActionCardDto> habitByKey = needHabit
                ? indexHabits(analysisId)
                : Map.of();

        for (GrowthCard card : cards) {
            List<GrowthCardEvidence> rows = buildRows(card, analysisId, expressions, habitByKey);
            if (CollectionUtils.isEmpty(rows)) {
                continue;
            }
            try {
                store.saveEvidence(rows);
            } catch (Exception ex) {
                log.warn("成长卡证据落库失败 cardId={}: {}", card.getCardId(), ex.getMessage());
            }
            // 落库失败也仍返回内存行，保证复习页能展示「查看证据」
            result.put(card.getCardId(), rows);
        }
    }

    private Map<String, ActionCardDto> indexHabits(String analysisId) {
        List<ConversationAnalysisItem> rows = itemMapper.findByAnalysisId(analysisId);
        if (CollectionUtils.isEmpty(rows)) {
            rows = List.of();
        }
        HabitCardScorer.HabitScoreResult scoreResult = analysisSupport.score(rows);
        Map<String, ActionCardDto> byKey = new HashMap<>();
        if (scoreResult.topHabit() != null) {
            putHabit(byKey, scoreResult.topHabit());
        }
        if (!CollectionUtils.isEmpty(scoreResult.actionCards())) {
            for (ActionCardDto card : scoreResult.actionCards()) {
                putHabit(byKey, card);
            }
        }
        return byKey;
    }

    private static void putHabit(Map<String, ActionCardDto> byKey, ActionCardDto habit) {
        if (ObjectUtils.isEmpty(habit)) {
            return;
        }
        String key = StringUtils.hasText(habit.getHabitKey()) ? habit.getHabitKey() : habit.getPointId();
        if (StringUtils.hasText(key)) {
            byKey.putIfAbsent(key, habit);
        }
    }

    private static List<GrowthCardEvidence> buildRows(
            GrowthCard card,
            String analysisId,
            List<ChineseExpressionDto> expressions,
            Map<String, ActionCardDto> habitByKey) {
        String sourceRef = card.getSourceRef().trim();
        long userId = card.getUserId() != null ? card.getUserId() : 0L;
        String cardId = card.getCardId();

        if (sourceRef.startsWith("vocab:")) {
            String indexText = sourceRef.substring("vocab:".length()).trim();
            Integer index = parseIndex(indexText);
            ChineseExpressionDto matched = findExpression(expressions, index, indexText);
            return GrowthCardEvidenceSupport.fromChineseExpression(userId, cardId, analysisId, matched);
        }

        if (sourceRef.startsWith("habit:")) {
            String habitKey = sourceRef.substring("habit:".length()).trim();
            ActionCardDto habit = habitByKey.get(habitKey);
            return GrowthCardEvidenceSupport.fromHabitExamples(userId, cardId, analysisId, habit);
        }

        return List.of();
    }

    private static Integer parseIndex(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ChineseExpressionDto findExpression(
            List<ChineseExpressionDto> expressions, Integer index, String indexText) {
        if (CollectionUtils.isEmpty(expressions)) {
            return null;
        }
        for (ChineseExpressionDto expression : expressions) {
            if (expression == null) {
                continue;
            }
            if (index != null && Objects.equals(expression.getOriginalIndex(), index)) {
                return expression;
            }
            if (index == null && StringUtils.hasText(indexText)
                    && indexText.equals(String.valueOf(expression.getOriginalIndex()))) {
                return expression;
            }
        }
        // 旧数据偶发缺 originalIndex：仅一张时直接用
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        return null;
    }
}
