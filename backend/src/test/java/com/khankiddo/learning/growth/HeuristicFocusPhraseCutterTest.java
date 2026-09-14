package com.khankiddo.learning.growth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicFocusPhraseCutterTest {

    private final HeuristicFocusPhraseCutter cutter = new HeuristicFocusPhraseCutter();

    @Test
    void cut_prefersPointWrongToCorrection_stripsChineseReason() {
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "I'm so exciting about the trip.",
                "exciting → excited（感到…用 -ed）",
                "I'm so excited about the trip.",
                "FEEL_ED_ADJ");

        Optional<FocusPhrasePair> result = cutter.cut(request);

        assertTrue(result.isPresent());
        assertEquals("exciting", result.get().focusWrong());
        assertEquals("excited", result.get().focusNatural());
    }

    @Test
    void cut_fallsBackToTokenAlignment_whenPointMissing() {
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "I need a paper for the meeting.",
                null,
                "I need a document for the meeting.",
                "LEXICAL_GAP");

        Optional<FocusPhrasePair> result = cutter.cut(request);

        assertTrue(result.isPresent());
        assertEquals("paper", result.get().focusWrong());
        assertEquals("document", result.get().focusNatural());
    }

    @Test
    void cut_returnsEmpty_whenSuggestionNotSubstantive() {
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "I feel happy.",
                "happy → Happy（大小写）",
                "I feel Happy.",
                "LEXICAL_GAP");

        assertTrue(cutter.cut(request).isEmpty());
    }

    @Test
    void cut_returnsEmpty_whenOnlyWholeSentenceRewriteWithoutPoint() {
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "The weather is good today.",
                null,
                "It is such a lovely day outside right now.",
                "LEXICAL_GAP");

        assertTrue(cutter.cut(request).isEmpty());
    }

    @Test
    void cut_usesCollocationPointFragments() {
        FocusPhraseCutRequest request = new FocusPhraseCutRequest(
                "I look forward to see you.",
                "look forward to see → look forward to seeing（to 后接动名词）",
                "I look forward to seeing you.",
                "COLLOCATION");

        Optional<FocusPhrasePair> result = cutter.cut(request);

        assertTrue(result.isPresent());
        assertEquals("look forward to see", result.get().focusWrong());
        assertEquals("look forward to seeing", result.get().focusNatural());
    }
}
