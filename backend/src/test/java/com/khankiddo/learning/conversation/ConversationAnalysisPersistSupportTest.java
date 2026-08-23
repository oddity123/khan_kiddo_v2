package com.khankiddo.learning.conversation;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.Test;

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
}
