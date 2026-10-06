package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.dto.shadowing.ShadowingScoreDto;
import com.khankiddo.learning.dto.shadowing.ShadowingWordDto;
import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowingWordMatcherTest {

    private static List<Boolean> hits(ShadowingScoreDto result) {
        return result.getWords().stream().map(ShadowingWordDto::isHit).toList();
    }

    @Test
    void exactMatchIgnoresCaseAndPunctuation() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "I have been working here, honestly.", "I HAVE BEEN WORKING HERE HONESTLY", 60);

        assertEquals(100, result.getScore());
        assertTrue(result.isPassed());
        assertEquals("I HAVE BEEN WORKING HERE HONESTLY", result.getRecognizedText());
        assertEquals(List.of("I", "have", "been", "working", "here,", "honestly."),
                result.getWords().stream().map(ShadowingWordDto::getText).toList());
        assertTrue(result.getWords().stream().allMatch(ShadowingWordDto::isHit));
    }

    @Test
    void missingWordsLowerTheScore() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "She goes to school every day", "she goes school day", 60);

        assertEquals(List.of(true, true, false, true, false, true), hits(result));
        assertEquals(67, result.getScore());
        assertTrue(result.isPassed());
    }

    @Test
    void wrongVerbFormIsNotForgiven() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "I went there yesterday", "i go there yesterday", 60);

        assertEquals(List.of(true, false, true, true), hits(result));
        assertEquals(75, result.getScore());
    }

    @Test
    void extraWordsDoNotCountAgainst() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "Thank you", "um thank you so much", 60);

        assertEquals(100, result.getScore());
    }

    @Test
    void orderMattersViaLongestCommonSubsequence() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "one two three four", "four three two one", 60);

        assertEquals(25, result.getScore());
        assertFalse(result.isPassed());
    }

    @Test
    void contractionsAndCurlyApostrophesMatch() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "I don’t think it's ready", "I DON'T THINK IT'S READY", 60);

        assertEquals(100, result.getScore());
    }

    @Test
    void hyphenatedTokenNeedsAllParts() {
        ShadowingScoreDto partial = ShadowingWordMatcher.match(
                "a well-known fact", "a well fact", 60);
        assertEquals(List.of(true, false, true), hits(partial));
        assertEquals(75, partial.getScore());

        ShadowingScoreDto full = ShadowingWordMatcher.match(
                "a well-known fact", "a well known fact", 60);
        assertEquals(100, full.getScore());
    }

    @Test
    void digitTokensAreNotScored() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "I waited 3 hours", "i waited three hours", 60);

        ShadowingWordDto digit = result.getWords().get(2);
        assertEquals("3", digit.getText());
        assertFalse(digit.isScored());
        assertFalse(digit.isHit());
        assertEquals(100, result.getScore());
    }

    @Test
    void punctuationOnlyTokensAreNotScored() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "Well — maybe", "well maybe", 60);

        assertFalse(result.getWords().get(1).isScored());
        assertEquals(100, result.getScore());
    }

    @Test
    void emptyRecognitionScoresZero() {
        ShadowingScoreDto result = ShadowingWordMatcher.match("Hello there", "  ", 60);

        assertEquals(0, result.getScore());
        assertFalse(result.isPassed());
        assertEquals("", result.getRecognizedText());
    }

    @Test
    void passThresholdIsInclusive() {
        ShadowingScoreDto result = ShadowingWordMatcher.match(
                "a b c d e", "a b c", 60);

        assertEquals(60, result.getScore());
        assertTrue(result.isPassed());
    }

    @Test
    void targetWithoutScorableWordsIsRejected() {
        assertThrows(BadRequestException.class, () -> ShadowingWordMatcher.match("123 456", "x", 60));
        assertThrows(BadRequestException.class, () -> ShadowingWordMatcher.match("  ", "x", 60));
    }
}
