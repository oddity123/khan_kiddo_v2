package com.khankiddo.learning.ai.grammar;

import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.ProblemTypeCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarLearningDbServiceTest {

    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private ConversationAnalysisMapper analysisMapper;

    private GrammarLearningDbService service;
    private GrammarStatsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GrammarStatsProperties();
        service = new GrammarLearningDbService(itemMapper, analysisMapper, properties);
    }

    @Test
    void buildStatsSummary_shouldIncludeTimeScopeAndFilter() {
        ProblemTypeCount tense = typeCount("Tense", 5L);
        ProblemTypeCount article = typeCount("Article", 2L);
        when(itemMapper.countProblemTypesByUserIdAndDays(1L, 7))
                .thenReturn(List.of(tense, article));

        String text = service.buildStatsSummary(1L, List.of("Tense"), 7);

        assertTrue(text.contains("近 7 天"));
        assertTrue(text.contains("Tense"));
        assertTrue(text.contains("5 次"));
        assertTrue(!text.contains("Article"));
    }

    @Test
    void buildErrorExamples_shouldFormatItemsAndClampLimit() {
        when(itemMapper.findErrorExamplesByUserId(eq(1L), isNull(), isNull(), eq(10)))
                .thenReturn(List.of(ConversationAnalysisItem.builder()
                        .analysisId("a1")
                        .problemTypes("Tense")
                        .originalSentence("He go")
                        .errorPoint("go → goes")
                        .suggestion("He goes")
                        .build()));

        String text = service.buildErrorExamples(1L, List.of(), null, 99);

        verify(itemMapper).findErrorExamplesByUserId(1L, null, null, 10);
        assertTrue(text.contains("He go"));
        assertTrue(text.contains("go → goes"));
        assertTrue(text.contains("analysisId：a1"));
    }

    @Test
    void buildPracticeOverview_shouldAggregate() {
        when(analysisMapper.countByUserIdAndStatusAndDays(1L, "success", 7)).thenReturn(3L);
        when(itemMapper.countDistinctErrorSentencesByUserIdAndDays(1L, 7)).thenReturn(12L);
        when(itemMapper.getMostCommonProblemTypeByUserIdAndDays(1L, 7))
                .thenReturn(Map.of("problemType", "Article", "count", 4L));

        String text = service.buildPracticeOverview(1L, 7);

        assertTrue(text.contains("成功分析次数：3"));
        assertTrue(text.contains("有错误的句子数：12"));
        assertTrue(text.contains("Article"));
        assertTrue(text.contains("4 次"));
    }

    @Test
    void normalizeDays_shouldCapAndTreatNonPositiveAsNull() {
        assertNull(service.normalizeDays(0));
        assertEquals(properties.getDb().getMaxDays(), service.normalizeDays(999));
        assertEquals(7, service.normalizeDays(7));
    }

    private static ProblemTypeCount typeCount(String type, Long count) {
        ProblemTypeCount row = new ProblemTypeCount();
        row.setProblemType(type);
        row.setCount(count);
        return row;
    }
}
