package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConversationAnalysisPipelineStage2Test {

    @Autowired
    private ConversationAnalysisPipeline pipeline;

    @Test
    void analyzeEnglishUtterances_empty_returnsEmptyItems() {
        GrammarAnalysisResult result =
                pipeline.analyzeEnglishUtterances(List.of(), null, p -> {});
        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }
}
