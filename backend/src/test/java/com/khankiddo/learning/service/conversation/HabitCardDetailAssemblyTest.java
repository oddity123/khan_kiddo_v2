package com.khankiddo.learning.service.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.conversation.ConversationAnalysisPipeline;
import com.khankiddo.learning.conversation.EducationalSummaryParser;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
@ExtendWith(MockitoExtension.class)
class HabitCardDetailAssemblyTest {

    @Mock
    private ConversationAnalysisPipeline pipeline;
    @Mock
    private ConversationAnalysisMapper analysisMapper;
    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private EducationalSummaryParser summaryParser;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConversationAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        HabitCardScorer habitCardScorer = new HabitCardScorer(dictionary);
        service = new ConversationAnalysisServiceImpl(
                pipeline, analysisMapper, itemMapper, summaryParser, objectMapper, eventPublisher,
                dictionary, habitCardScorer);
    }

    @Test
    void allRowsWithPointId_producesTopHabitAndActionCards() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, "ARTICLE_A_AN", "Tense", "a apple", "an apple"),
                row(1L, "ARTICLE_A_AN", "Tense", "a hour", "an hour")
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
                row(1L, null, "Tense", "a apple", "an apple"),
                row(1L, null, "Tense", "a hour", "an hour")
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
                row(1L, "ARTICLE_A_AN", "Tense", "a apple", "an apple"),
                row(2L, null, "Tense", "some odd error", null)
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(rows, List.of());

        // 混合数据仍应参与打分（不因个别行缺 pointId 而整体跳过）。
        assertTrue(result.familyDistribution().stream().mapToInt(d -> d.getCount()).sum() == 2);
    }

    @Test
    void chineseExpressionsArePassedThroughToScorer() {
        List<ConversationAnalysisItem> rows = List.of(
                row(1L, "ARTICLE_A_AN", "Tense", "a apple", "an apple"),
                row(1L, "ARTICLE_A_AN", "Tense", "a hour", "an hour")
        );
        List<ChineseExpressionDto> chinese = List.of(
                ChineseExpressionDto.builder()
                        .originalIndex(0)
                        .originalSentence("我觉得立法很重要")
                        .focusPhrase("立法")
                        .suggestion("legislation")
                        .build(),
                ChineseExpressionDto.builder()
                        .originalIndex(1)
                        .originalSentence("这个客商很有名")
                        .focusPhrase("客商")
                        .suggestion("client")
                        .build(),
                ChineseExpressionDto.builder()
                        .originalIndex(2)
                        .originalSentence("他喜欢敲碗")
                        .focusPhrase("敲碗")
                        .suggestion("tap the bowl")
                        .build()
        );

        HabitCardScorer.HabitScoreResult result = service.buildHabitScoreResult(rows, chinese);

        assertTrue(result.actionCards().stream()
                .anyMatch(card -> "CHINESE_CODE_SWITCH".equals(card.getPointId())));
    }

    private static ConversationAnalysisItem row(
            Long sentenceId, String pointId, String problemType, String errorPoint, String suggestion) {
        return ConversationAnalysisItem.builder()
                .analysisId("a1")
                .sentenceId(sentenceId)
                .originalSentence("I eat " + errorPoint + ".")
                .problemTypes(problemType)
                .pointId(pointId)
                .errorPoint(errorPoint)
                .suggestion(suggestion)
                .build();
    }
}
