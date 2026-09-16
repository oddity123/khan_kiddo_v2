package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarErrorDto;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HabitScoreSupportTest {

    private static PointDictionary dictionary;

    @BeforeAll
    static void setUpDictionary() {
        dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
    }

    @Test
    void errorHitsFromItems_mapsPointAndLevel() {
        ConversationAnalysisItem row = ConversationAnalysisItem.builder()
                .sentenceId(3L)
                .pointId("ARTICLE_A_AN")
                .originalSentence("a apple")
                .errorPoint("a → an")
                .suggestion("an apple")
                .build();

        List<HabitScoreInput.ErrorHit> hits = HabitScoreSupport.errorHitsFromItems(List.of(row), dictionary);

        assertEquals(1, hits.size());
        assertEquals("ARTICLE_A_AN", hits.get(0).pointId());
        assertEquals("3", hits.get(0).sentenceId());
        assertEquals(HabitScoreSupport.resolveErrorLevel("ARTICLE_A_AN", dictionary), hits.get(0).errorLevel());
        assertTrue(HabitScoreSupport.hasAnyPointId(List.of(row)));
        assertFalse(HabitScoreSupport.hasAnyPointId(List.of()));
    }

    @Test
    void errorHitsFromGrammar_usesSentenceOrdinal() {
        GrammarAnalysisResult grammar = GrammarAnalysisResult.builder()
                .items(List.of(
                        GrammarSentenceItemDto.builder()
                                .originalSentence("a apple")
                                .suggestion("an apple")
                                .errors(List.of(GrammarErrorDto.builder()
                                        .pointId("ARTICLE_A_AN")
                                        .point("a → an")
                                        .build()))
                                .build()))
                .build();

        List<HabitScoreInput.ErrorHit> hits = HabitScoreSupport.errorHitsFromGrammar(grammar, dictionary);

        assertEquals(1, hits.size());
        assertEquals("1", hits.get(0).sentenceId());
        assertEquals("ARTICLE_A_AN", hits.get(0).pointId());
    }
}
