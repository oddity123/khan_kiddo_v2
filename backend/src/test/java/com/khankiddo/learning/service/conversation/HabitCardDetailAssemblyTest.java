package com.khankiddo.learning.service.conversation;

import com.khankiddo.learning.dto.conversation.ActionCardDiagnosisDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详情装配：{@link ConversationAnalysisServiceImpl#buildHabitScoreResult} 从持久化的
 * {@code ConversationAnalysisItem} 行 + 中文表达组装 topHabit / actionCards / familyDistribution。
 */
class HabitCardDetailAssemblyTest {

    private ConversationAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer habitCardScorer = new HabitCardScorer(dictionary);
        service = new ConversationAnalysisServiceImpl(
                null, null, null, null, null, null, dictionary, habitCardScorer, null, null);
    }

    @Test
    void allRowsWithPointId_producesTopHabitAndActionCards() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, "ARTICLE_A_AN", "a apple", "an apple"),
                row(1L, "ARTICLE_A_AN", "a hour", "an hour")
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(rows, List.of());

        assertNotNull(result.topHabit());
        assertEquals("ARTICLE_A_AN", result.topHabit().getPointId());
        assertEquals(1, result.actionCards().size());
        assertEquals(1, result.familyDistribution().size());
    }

    @Test
    void allRowsMissingPointId_skipsScoringEntirely() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, null, "a apple", "an apple"),
                row(1L, null, "a hour", "an hour")
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(rows, List.of());

        assertNull(result.topHabit());
        assertTrue(result.actionCards().isEmpty());
        assertTrue(result.familyDistribution().isEmpty());
    }

    @Test
    void noRowsAtAll_skipsScoringEntirely() {
        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(new ArrayList<>(), List.of());

        assertNull(result.topHabit());
        assertTrue(result.actionCards().isEmpty());
        assertTrue(result.familyDistribution().isEmpty());
    }

    @Test
    void mixedPointIds_stillScoresUsingFallbackForMissingOnes() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, "ARTICLE_A_AN", "a apple", "an apple"),
                row(2L, null, "some odd error", null)
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(rows, List.of());

        // 混合数据仍应参与打分（不因个别行缺 pointId 而整体跳过）。
        assertTrue(result.familyDistribution().stream().mapToInt(d -> d.getCount()).sum() == 2);
    }

    @Test
    void chineseContentExpressionsArePassedThroughToScorer() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, "ARTICLE_A_AN", "a apple", "an apple"),
                row(1L, "ARTICLE_A_AN", "a hour", "an hour")
        );
        // 内容表达（无 focusPhrase）才计入习惯打分；词汇求助不进 Top
        List<ChineseExpressionDto> chinese = List.of(
                ChineseExpressionDto.builder()
                        .originalIndex(0)
                        .originalSentence("我觉得立法很重要")
                        .focusPhrase("")
                        .suggestion("I think legislation is important.")
                        .build(),
                ChineseExpressionDto.builder()
                        .originalIndex(1)
                        .originalSentence("这个客商很有名")
                        .focusPhrase("")
                        .suggestion("This client is well known.")
                        .build(),
                ChineseExpressionDto.builder()
                        .originalIndex(2)
                        .originalSentence("他喜欢敲碗")
                        .focusPhrase("")
                        .suggestion("He likes to tap the bowl.")
                        .build()
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(rows, chinese);

        assertTrue(result.actionCards().stream()
                .anyMatch(card -> "CHINESE_CODE_SWITCH".equals(card.getPointId())));
    }

    @Test
    void diagnosisIsMergedIntoActionCardByHabitKey() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, "ARTICLE_A_AN", "a apple", "an apple"),
                row(2L, "ARTICLE_A_AN", "a hour", "an hour")
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(
                rows,
                List.of(),
                List.of(ActionCardDiagnosisDto.builder()
                        .rank(1)
                        .habitKey("FAM_ARTICLE")
                        .pointId("ARTICLE_A_AN")
                        .diagnosisZh("这次冠词问题集中在 a/an 的读音判断，说明单数名词前的冠词选择还不够稳定。")
                        .build()));

        assertEquals("冠词", result.topHabit().getTitleZh());
        assertEquals("这次冠词问题集中在 a/an 的读音判断，说明单数名词前的冠词选择还不够稳定。",
                result.topHabit().getDiagnosisZh());
        assertTrue(result.topHabit().getWhyZh().contains("其中可先练"));
    }

    private static ConversationAnalysisItem row(
            Long sentenceId, String pointId, String errorPoint, String suggestion) {
        return ConversationAnalysisItem.builder()
                .analysisId("a1")
                .sentenceId(sentenceId)
                .originalSentence("I eat " + errorPoint + ".")
                .pointId(pointId)
                .errorPoint(errorPoint)
                .suggestion(suggestion)
                .build();
    }
}
