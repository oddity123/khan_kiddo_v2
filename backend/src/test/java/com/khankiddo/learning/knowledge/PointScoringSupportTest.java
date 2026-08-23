package com.khankiddo.learning.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointScoringSupportTest {

    private static final PointDictionary DICTIONARY =
            PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");

    @Test
    void resolvesScoreProfileFromDictionaryField() {
        PointDefinition tense = DICTIONARY.require("PAST_SIMPLE_DONE");
        assertThat(PointScoringSupport.scoreProfile(tense)).isEqualTo("TENSE");
        assertThat(PointScoringSupport.errorLevel(tense)).isEqualTo("FATAL");
        assertThat(PointScoringSupport.isFatal(tense)).isTrue();
    }

    @Test
    void resolvesBasicPrepositionProfile() {
        PointDefinition prep = DICTIONARY.require("PREP_FIXED");
        assertThat(PointScoringSupport.scoreProfile(prep)).isEqualTo("PREPOSITION");
        assertThat(PointScoringSupport.isFatal(prep)).isFalse();
    }
}
