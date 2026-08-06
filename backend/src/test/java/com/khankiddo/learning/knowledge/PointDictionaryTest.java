package com.khankiddo.learning.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointDictionaryTest {

    @Test
    void loadsAndResolvesFeelEdAdj() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        PointDefinition p = dict.require("FEEL_ED_ADJ");
        assertEquals("FAM_WORD_FORM", p.familyId());
        assertEquals(PointChannel.RULE, p.channel());
        assertEquals(CardKind.GRAMMAR, p.cardKind());
        assertEquals("Word Form", p.problemType());
    }

    @Test
    void unknownFallsBackToStructureOther() {
        PointDictionary dict = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        PointDefinition p = dict.resolveOrFallback("NOT_A_REAL_POINT");
        assertEquals("STRUCTURE_OTHER", p.pointId());
    }
}
