package com.khankiddo.learning.conversation;

import com.khankiddo.learning.config.ConversationAnalysisProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisTimeoutBudgetTest {

    @Test
    void worstCaseStaysUnderSseWindow() {
        ConversationAnalysisProperties properties = new ConversationAnalysisProperties();

        assertThat(ConversationAnalysisTimeoutBudget.worstCaseAnalysis(properties, 14))
                .isLessThan(ConversationAnalysisTimeoutBudget.SSE_TIMEOUT);
        assertThat(ConversationAnalysisTimeoutBudget.worstCaseAnalysis(properties, 20))
                .isLessThan(ConversationAnalysisTimeoutBudget.SSE_TIMEOUT);
        assertThat(ConversationAnalysisTimeoutBudget.worstCaseAnalysis(properties, 50))
                .isLessThan(ConversationAnalysisTimeoutBudget.SSE_TIMEOUT);
    }

    @Test
    void batchedWorstCaseUsesWavesNotRawBatchCount() {
        ConversationAnalysisProperties properties = new ConversationAnalysisProperties();
        Duration batched = ConversationAnalysisTimeoutBudget.worstCaseStage2(properties, 20);

        assertThat(batched).isEqualTo(properties.getChatTimeout().multipliedBy(2));
    }
}
