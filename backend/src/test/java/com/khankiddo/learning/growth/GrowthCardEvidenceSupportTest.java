package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.model.GrowthCardEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthCardEvidenceSupportTest {

    @Test
    void fromHabitExamples_shouldDedupeBySentenceId() {
        ActionCardDto habit = ActionCardDto.builder()
                .examples(List.of(
                        ActionCardDto.ExampleDto.builder()
                                .sentenceId("s1")
                                .originalSentence("I go yesterday.")
                                .suggestion("I went yesterday.")
                                .build(),
                        ActionCardDto.ExampleDto.builder()
                                .sentenceId("s1")
                                .originalSentence("I go yesterday again.")
                                .suggestion("I went yesterday again.")
                                .build(),
                        ActionCardDto.ExampleDto.builder()
                                .sentenceId("s2")
                                .originalSentence("She go home.")
                                .suggestion("She went home.")
                                .build()
                ))
                .build();

        List<GrowthCardEvidence> rows =
                GrowthCardEvidenceSupport.fromHabitExamples(9L, "card-1", "analysis-1", habit);

        assertEquals(2, rows.size());
        assertEquals("s:s1", rows.get(0).getTrackKey());
        assertEquals("I go yesterday.", rows.get(0).getOriginalSentence());
        assertEquals("s:s2", rows.get(1).getTrackKey());
        assertEquals(9L, rows.get(0).getUserId());
        assertEquals("card-1", rows.get(0).getCardId());
        assertEquals("analysis-1", rows.get(0).getSourceAnalysisId());
    }

    @Test
    void fromHabitExamples_shouldFallbackTrackKeyWhenNoSentenceId() {
        ActionCardDto habit = ActionCardDto.builder()
                .examples(List.of(
                        ActionCardDto.ExampleDto.builder()
                                .originalSentence("  Hello   World  ")
                                .build(),
                        ActionCardDto.ExampleDto.builder()
                                .originalSentence("hello world")
                                .build()
                ))
                .build();

        List<GrowthCardEvidence> rows =
                GrowthCardEvidenceSupport.fromHabitExamples(1L, "c1", "a1", habit);

        assertEquals(1, rows.size());
        assertEquals("t:hello world", rows.get(0).getTrackKey());
        assertTrue(rows.get(0).getSentenceId() == null || rows.get(0).getSentenceId().isBlank());
    }

    @Test
    void fromChineseExpression_shouldUseOriginalIndexAsSentenceId() {
        ChineseExpressionDto expression = ChineseExpressionDto.builder()
                .originalIndex(3)
                .originalSentence("这个怎么说")
                .suggestion("How do you say this?")
                .build();

        List<GrowthCardEvidence> rows =
                GrowthCardEvidenceSupport.fromChineseExpression(2L, "c2", "a2", expression);

        assertEquals(1, rows.size());
        assertEquals("3", rows.get(0).getSentenceId());
        assertEquals("s:3", rows.get(0).getTrackKey());
        assertEquals("How do you say this?", rows.get(0).getSuggestion());
    }
}
