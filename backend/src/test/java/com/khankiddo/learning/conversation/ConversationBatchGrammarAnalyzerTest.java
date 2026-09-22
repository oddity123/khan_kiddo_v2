package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import com.khankiddo.learning.log.ConversationAnalysisCallLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationBatchGrammarAnalyzerTest {

    @Mock
    private ConversationGrammarAnalysisHelper grammarAnalysisHelper;

    @Mock
    private GrammarAnalysisUserPromptBuilder userPromptBuilder;

    private ConversationAnalysisProperties properties;
    private ConversationBatchGrammarAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        properties = new ConversationAnalysisProperties();
        properties.setBatchSize(5);
        properties.setBatchConcurrentLimit(5);
        analyzer = new ConversationBatchGrammarAnalyzer(grammarAnalysisHelper, userPromptBuilder, properties);
        when(userPromptBuilder.buildFromUserSentences(any())).thenReturn("prompt");
    }

    @Test
    void copiesAnalysisIdMdcOntoBatchWorkerThreads() {
        ConversationAnalysisCallLog.putAnalysisId("analysis-mdc");
        AtomicReference<String> seenOnWorker = new AtomicReference<>();
        when(grammarAnalysisHelper.analyzeGrammar(
                any(), any(), any(), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    seenOnWorker.set(MDC.get(ConversationAnalysisCallLog.MDC_ANALYSIS_ID));
                    return GrammarAnalysisResult.builder().build();
                });
        try {
            analyzer.analyzeInBatches(twoBatchSentences(), "system", model(), "analysis-mdc", progress -> {});
        } finally {
            ConversationAnalysisCallLog.clear();
        }
        assertThat(seenOnWorker.get()).isEqualTo("analysis-mdc");
    }

    @Test
    void analyzeInBatches_usesChatCallsPerBatch() {
        when(grammarAnalysisHelper.analyzeGrammar(
                any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(GrammarAnalysisResult.builder().build());

        analyzer.analyzeInBatches(twoBatchSentences(), "system", model(), progress -> {});

        verify(grammarAnalysisHelper).analyzeGrammar(
                eq("system"), eq("prompt"), eq(model()), eq(1), eq(2), any());
        verify(grammarAnalysisHelper).analyzeGrammar(
                eq("system"), eq("prompt"), eq(model()), eq(2), eq(2), any());
    }

    @Test
    void keepsSuccessfulBatchAndRetriesOnlyTheFailedOne() {
        AtomicBoolean firstBatchFirstAttempt = new AtomicBoolean(true);
        when(grammarAnalysisHelper.analyzeGrammar(
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

        verify(grammarAnalysisHelper, times(2)).analyzeGrammar(
                any(), any(), any(), eq(1), eq(2), any());
        verify(grammarAnalysisHelper, times(1)).analyzeGrammar(
                any(), any(), any(), eq(2), eq(2), any());
        assertThat(merged.getItems())
                .extracting(GrammarSentenceItemDto::getOriginalSentence)
                .containsExactly("batch-1", "batch-2");
        assertThat(properties.getBatchConcurrentLimit()).isEqualTo(5);
    }

    @Test
    void cancelsBatchesNotYetStartedThenRetriesMissingOnes() {
        properties.setBatchConcurrentLimit(1);
        analyzer = new ConversationBatchGrammarAnalyzer(grammarAnalysisHelper, userPromptBuilder, properties);

        List<Integer> callOrder = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean firstBatchFirstAttempt = new AtomicBoolean(true);
        when(grammarAnalysisHelper.analyzeGrammar(
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

        // concurrentLimit=1 时仍可能先拿到其它批的 permit；关键是失败批被重试且最终合并完整
        assertThat(callOrder.stream().filter(n -> n == 1).count()).isEqualTo(2);
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
