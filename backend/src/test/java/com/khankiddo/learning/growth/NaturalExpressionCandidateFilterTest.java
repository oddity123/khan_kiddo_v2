package com.khankiddo.learning.growth;

import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalExpressionCandidateFilterTest {

    private static PointDictionary dictionary;
    private static NaturalExpressionCandidateFilter filter;

    @BeforeAll
    static void setUp() {
        dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        filter = new NaturalExpressionCandidateFilter(dictionary);
    }

    @Test
    void acceptsNaturalWithSubstantiveSuggestion() {
        assertTrue(filter.test(item("COLLOCATION",
                "I do a role-play every week.",
                "do a role-play → do role-plays（固定搭配）",
                "I do role-plays every week.")));
    }

    @Test
    void acceptsNaturalWithUsablePointEvenIfSuggestionThin() {
        // point 片段可用即可过闸；实质对立由切分策略再判
        assertTrue(filter.test(item("FEEL_ED_ADJ",
                "I'm so exciting.",
                "exciting → excited（感到…用 -ed）",
                "I'm so exciting.")));
    }

    @Test
    void rejectsStyleEvenWithSuggestion() {
        assertFalse(filter.test(item("SENTENCE_LOOSE_AND",
                "I went home and I ate dinner and I slept.",
                "and I → , then I（松散并列）",
                "I went home, then I ate dinner, then I slept.")));
    }

    @Test
    void rejectsBasicErrorLevel() {
        assertFalse(filter.test(item("PLURAL_COUNTABLE",
                "I have three apple.",
                "apple → apples（可数名词复数）",
                "I have three apples.")));
    }

    @Test
    void rejectsWhenNeitherSuggestionNorPointUsable() {
        assertFalse(filter.test(item("LEXICAL_GAP",
                "Hello.",
                null,
                null)));
    }

    @Test
    void rejectsCaseOnlySuggestionWithoutPoint() {
        assertFalse(filter.test(item("LEXICAL_GAP",
                "i like tea.",
                null,
                "I like tea.")));
    }

    private static ConversationAnalysisItem item(
            String pointId, String original, String errorPoint, String suggestion) {
        return ConversationAnalysisItem.builder()
                .sentenceId(1L)
                .pointId(pointId)
                .originalSentence(original)
                .errorPoint(errorPoint)
                .suggestion(suggestion)
                .build();
    }
}
