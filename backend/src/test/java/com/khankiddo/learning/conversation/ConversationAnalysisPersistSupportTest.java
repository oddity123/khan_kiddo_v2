package com.khankiddo.learning.conversation;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisPersistSupportTest {

    @Test
    void truncatesErrorPointToFiveHundredChars() {
        String tooLong = "x".repeat(501);

        String truncated = ConversationAnalysisPersistSupport.truncate(
                tooLong, ConversationAnalysisPersistSupport.ERROR_POINT_MAX);

        assertThat(truncated).hasSize(500);
        assertThat(truncated).isEqualTo("x".repeat(500));
    }

    @Test
    void keepsShortErrorPointUnchanged() {
        assertThat(ConversationAnalysisPersistSupport.truncate(
                "go → goes", ConversationAnalysisPersistSupport.ERROR_POINT_MAX))
                .isEqualTo("go → goes");
    }

    @Test
    void truncatesItemVarcharFieldsToColumnLimits() {
        ConversationAnalysisItem item = ConversationAnalysisItem.builder()
                .analysisId("a".repeat(80))
                .pointId("p".repeat(60))
                .errorPoint("e".repeat(600))
                .suggestion("keep me")
                .originalSentence("I go.")
                .build();

        ConversationAnalysisPersistSupport.truncateItem(item);

        assertThat(item.getAnalysisId()).hasSize(64);
        assertThat(item.getPointId()).hasSize(48);
        assertThat(item.getErrorPoint()).hasSize(500);
        assertThat(item.getSuggestion()).isEqualTo("keep me");
        assertThat(item.getOriginalSentence()).isEqualTo("I go.");
    }

    @Test
    void resolveSentenceId_prefersPipelineIdOverReassign() {
        Map<String, Long> byText = new HashMap<>();
        AtomicLong next = new AtomicLong(1L);

        Long resolved = ConversationAnalysisPersistSupport.resolveSentenceId(
                5L, "I need a apple.", byText, next);

        assertThat(resolved).isEqualTo(5L);
        assertThat(byText).isEmpty();
        assertThat(next.get()).isEqualTo(1L);
    }

    @Test
    void resolveSentenceId_fallsBackToStableCounterWhenPipelineIdMissing() {
        Map<String, Long> byText = new HashMap<>();
        AtomicLong next = new AtomicLong(1L);

        Long first = ConversationAnalysisPersistSupport.resolveSentenceId(
                null, "Clean sentence.", byText, next);
        Long again = ConversationAnalysisPersistSupport.resolveSentenceId(
                null, "Clean sentence.", byText, next);
        Long other = ConversationAnalysisPersistSupport.resolveSentenceId(
                null, "Other sentence.", byText, next);

        assertThat(first).isEqualTo(1L);
        assertThat(again).isEqualTo(1L);
        assertThat(other).isEqualTo(2L);
    }
}
