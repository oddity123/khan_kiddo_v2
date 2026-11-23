package com.khankiddo.learning.memory;

import com.khankiddo.learning.memory.assessment.ConversationTranscriptSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationTranscriptSupportTest {

    @Test
    void stripsInjectedCoachPromptTurnAndKeepsLearnerSpeech() {
        String raw = """
                User: # Spoken English Warm-up Coach
                # Review Material
                ## Phase 1 — Recap
                Yesterday I went to the store.

                Assistant: 请先开启语音练习，开麦后我们开始。

                User: Last weekend I went to a bookstore with my cousin.

                Assistant: Nice! What did you buy?
                """;

        String cleaned = ConversationTranscriptSupport.extractCleanTranscript(raw, 8000);
        assertFalse(cleaned.contains("Spoken English Warm-up Coach"));
        assertTrue(cleaned.contains("User: Last weekend I went to a bookstore with my cousin."));

        List<String> utterances = ConversationTranscriptSupport.extractCleanUserUtterances(raw);
        assertEquals(1, utterances.size());
        assertEquals("Last weekend I went to a bookstore with my cousin.", utterances.get(0));

        // 注入指令中的示范句不得通过落盘校验
        assertFalse(ConversationTranscriptSupport.isQuoteGroundedInUserSpeech(
                "Yesterday I went to the store.", utterances));
        assertTrue(ConversationTranscriptSupport.isQuoteGroundedInUserSpeech(
                "Last weekend I went to a bookstore", utterances));
    }
}
