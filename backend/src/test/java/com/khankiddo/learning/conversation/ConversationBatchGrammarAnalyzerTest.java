package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationBatchGrammarAnalyzerTest {

    @Mock
    private ConversationAnalysisStreamingHelper streamingHelper;

    @Mock
    private GrammarAnalysisUserPromptBuilder userPromptBuilder;

    private ConversationAnalysisProperties properties;
    private ConversationBatchGrammarAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        properties = new ConversationAnalysisProperties();
        properties.setBatchSize(5);
        properties.setBatchConcurrentLimit(5);
        analyzer = new ConversationBatchGrammarAnalyzer(streamingHelper, userPromptBuilder, properties);
        when(userPromptBuilder.buildFromUserSentences(any())).thenReturn("prompt");
    }

    @Test
    void analyzeInBatches_usesNonStreamingCalls() {
        when(streamingHelper.analyzeGrammarWithoutStreaming(
                any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(GrammarAnalysisResult.builder().build());

        analyzer.analyzeInBatches(twoBatchSentences(), "system", model(), progress -> {});

        verify(streamingHelper, never()).streamGrammarAnalysis(
                any(), any(), any(), any());
        verify(streamingHelper, never()).streamGrammarAnalysis(
                any(), any(), any(), anyInt(), anyInt(), any());
        verify(streamingHelper).analyzeGrammarWithoutStreaming(
                eq("system"), eq("prompt"), eq(model()), eq(1), eq(2), any());
        verify(streamingHelper).analyzeGrammarWithoutStreaming(
                eq("system"), eq("prompt"), eq(model()), eq(2), eq(2), any());
    }

    @Test
    void keepsSuccessfulBatchAndRetriesOnlyTheFailedOne() {
        AtomicBoolean firstBatchFirstAttempt = new AtomicBoolean(true);
        when(streamingHelper.analyzeGrammarWithoutStreaming(
                any(), any(), any(), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    int batchNum = invocation.getArgument(3);
                    if (batchNum == 1 && firstBatchFirstAttempt.getAndSet(false)) {
                        throw new BadRequestException("AI 分析失败: connection reset");
                    }
                    return resultFor("batch-" + batchNum);
                });

        GrammarAnalysisResult merged = analyzer.analyzeInBatches(
                twoBatchSentences(), "system", model(), "analysis-1", progress -> {});

        verify(streamingHelper, times(2)).analyzeGrammarWithoutStreaming(
                any(), any(), any(), eq(1), eq(2), any());
        verify(streamingHelper, times(1)).analyzeGrammarWithoutStreaming(
                any(), any(), any(), eq(2), eq(2), any());
        assertThat(merged.getItems())
                .extracting(GrammarSentenceItemDto::getOriginalSentence)
                .containsExactly("batch-1", "batch-2");
        assertThat(properties.getBatchConcurrentLimit()).isEqualTo(5);
    }

    @Test
    void cancelsBatchesNotYetStartedThenRetriesMissingOnes() {
        properties.setBatchConcurrentLimit(1);
        analyzer = new ConversationBatchGrammarAnalyzer(streamingHelper, userPromptBuilder, properties);

        List<Integer> callOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean firstBatchFirstAttempt = new AtomicBoolean(true);
        when(streamingHelper.analyzeGrammarWithoutStreaming(
                any(), any(), any(), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    int batchNum = invocation.getArgument(3);
                    callOrder.add(batchNum);
                    if (batchNum == 1 && firstBatchFirstAttempt.getAndSet(false)) {
                        throw new BadRequestException("AI 分析失败: connection reset");
                    }
                    return resultFor("batch-" + batchNum);
                });

        GrammarAnalysisResult merged = analyzer.analyzeInBatches(
                threeBatchSentences(), "system", model(), "analysis-2", progress -> {});

        assertThat(callOrder.subList(0, 2)).containsExactly(1, 1);
        assertThat(callOrder).contains(2, 3);
        assertThat(merged.getItems())
                .extracting(GrammarSentenceItemDto::getOriginalSentence)
                .containsExactly("batch-1", "batch-2", "batch-3");
    }

    private static ResolvedLlmModel model() {
        return ResolvedLlmModel.builder().id("doubao-seed").build();
    }

    private static List<String> twoBatchSentences() {
        return List.of(
                "I go.", "She go.", "He go.", "We go.", "They go.",
                "You go.");
    }

    private static List<String> threeBatchSentences() {
        return List.of(
                "I go.", "She go.", "He go.", "We go.", "They go.",
                "You go.", "It go.", "Tom go.", "Ann go.", "Bob go.",
                "Ken go.");
    }

    private static GrammarAnalysisResult resultFor(String original) {
        return GrammarAnalysisResult.builder()
                .items(List.of(GrammarSentenceItemDto.builder().originalSentence(original).build()))
                .build();
    }
}
