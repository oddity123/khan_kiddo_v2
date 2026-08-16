package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationBatchGrammarAnalyzerTest {

    @Mock
    private ConversationAnalysisStreamingHelper streamingHelper;

    @Mock
    private GrammarAnalysisUserPromptBuilder userPromptBuilder;

    private ConversationBatchGrammarAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        ConversationAnalysisProperties properties = new ConversationAnalysisProperties();
        properties.setBatchSize(5);
        properties.setBatchConcurrentLimit(5);
        analyzer = new ConversationBatchGrammarAnalyzer(streamingHelper, userPromptBuilder, properties);
        when(userPromptBuilder.buildFromUserSentences(any())).thenReturn("prompt");
        when(streamingHelper.analyzeGrammarWithoutStreaming(
                any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(GrammarAnalysisResult.builder().build());
    }

    @Test
    void analyzeInBatches_usesNonStreamingCalls() {
        List<String> sentences = List.of(
                "I go.", "She go.", "He go.", "We go.", "They go.",
                "You go.");
        ResolvedLlmModel model = ResolvedLlmModel.builder().id("doubao-seed").build();

        analyzer.analyzeInBatches(sentences, "system", model, progress -> {});

        verify(streamingHelper, never()).streamGrammarAnalysis(
                any(), any(), any(), any());
        verify(streamingHelper, never()).streamGrammarAnalysis(
                any(), any(), any(), anyInt(), anyInt(), any());
        verify(streamingHelper).analyzeGrammarWithoutStreaming(
                eq("system"), eq("prompt"), eq(model), eq(1), eq(2), any());
        verify(streamingHelper).analyzeGrammarWithoutStreaming(
                eq("system"), eq("prompt"), eq(model), eq(2), eq(2), any());
    }
}
