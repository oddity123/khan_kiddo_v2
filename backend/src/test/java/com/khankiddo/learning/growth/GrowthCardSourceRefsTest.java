package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthCardSourceRefsTest {

    @Test
    void resolveHabitKey_prefersHabitKeyOverPointId() {
        ActionCardDto withKey = ActionCardDto.builder()
                .habitKey("FAM_ARTICLE")
                .pointId("ART_A")
                .build();
        assertEquals("FAM_ARTICLE", GrowthCardSourceRefs.resolveHabitKey(withKey));

        ActionCardDto pointOnly = ActionCardDto.builder().pointId("ART_A").build();
        assertEquals("ART_A", GrowthCardSourceRefs.resolveHabitKey(pointOnly));
    }

    @Test
    void encode_vocabHabitExpr() {
        assertEquals("vocab:3", GrowthCardSourceRefs.vocab(3));
        assertEquals("habit:FAM_ARTICLE", GrowthCardSourceRefs.habit(
                ActionCardDto.builder().habitKey("FAM_ARTICLE").build()));

        ConversationAnalysisItem withSentence = ConversationAnalysisItem.builder()
                .sentenceId(11L)
                .pointId("NAT_1")
                .build();
        assertEquals("expr:11", GrowthCardSourceRefs.expression(withSentence));

        ConversationAnalysisItem hashFallback = ConversationAnalysisItem.builder()
                .originalSentence("I need a paper.")
                .pointId("NAT_1")
                .errorPoint("paper → document")
                .build();
        String hashRef = GrowthCardSourceRefs.expression(hashFallback);
        assertTrue(hashRef.startsWith("expr:h:"));
        assertFalse(GrowthCardSourceRefs.isExprSentence(hashRef));
    }

    @Test
    void decode_prefixes() {
        assertEquals("3", GrowthCardSourceRefs.vocabIndexText("vocab:3"));
        assertEquals("FAM_ARTICLE", GrowthCardSourceRefs.habitKeyOf("habit:FAM_ARTICLE"));
        assertEquals("11", GrowthCardSourceRefs.sentenceIdOf("expr:11"));
        assertTrue(GrowthCardSourceRefs.isHabit("habit:x"));
        assertTrue(GrowthCardSourceRefs.isVocab("vocab:1"));
        assertTrue(GrowthCardSourceRefs.isExpr("expr:h:abc"));
    }
}
