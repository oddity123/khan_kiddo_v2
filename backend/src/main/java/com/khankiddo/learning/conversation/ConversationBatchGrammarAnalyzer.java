package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 用户句数超过阈值时，按 {@link ConversationAnalysisProperties#getBatchSize()} 均分切批并发流式分析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationBatchGrammarAnalyzer {

    private final ConversationAnalysisStreamingHelper streamingHelper;
    private final PromptLoader promptLoader;
    private final ConversationAnalysisProperties properties;

    public GrammarAnalysisResult analyzeInBatches(
            List<String> userSentences,
            String systemPrompt,
            Consumer<ConversationAnalysisProgress> onProgress) {

        if (CollectionUtils.isEmpty(userSentences)) {
            return GrammarAnalysisResult.builder().build();
        }

        List<List<String>> batches = splitBatches(userSentences, properties.getBatchSize());
        int totalBatches = batches.size();

        onProgress.accept(ConversationAnalysisProgress.builder()
                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                .message("正在分析（共 " + totalBatches + " 批）...")
                .build());

        Semaphore semaphore = new Semaphore(properties.getBatchConcurrentLimit());
        AtomicInteger completedCount = new AtomicInteger(0);
        List<GrammarAnalysisResult> orderedResults =
                Collections.synchronizedList(new ArrayList<>(Collections.nCopies(totalBatches, null)));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                final int index = batchIndex;
                final int batchNum = batchIndex + 1;
                final List<String> batchSentences = batches.get(batchIndex);
                futures.add(executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        String userPrompt = buildBatchUserPrompt(batchSentences);
                        GrammarAnalysisResult result = streamingHelper.streamGrammarAnalysis(
                                systemPrompt, userPrompt, batchNum, totalBatches, onProgress);
                        orderedResults.set(index, result);
                        int done = completedCount.incrementAndGet();
                        onProgress.accept(ConversationAnalysisProgress.builder()
                                .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                                .message("已完成 " + done + "/" + totalBatches + " 批")
                                .build());
                        log.info("分批语法分析第 {}/{} 批完成，本批 {} 句", batchNum, totalBatches, batchSentences.size());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new BadRequestException("分批分析被中断");
                    } finally {
                        semaphore.release();
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof BadRequestException badRequest) {
                        throw badRequest;
                    }
                    throw new BadRequestException("分批分析失败: " + cause.getMessage());
                }
            }
        }

        return mergeResults(orderedResults);
    }

    static List<List<String>> splitBatches(List<String> userSentences, int batchSize) {
        if (CollectionUtils.isEmpty(userSentences) || batchSize <= 0) {
            return List.of();
        }
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < userSentences.size(); i += batchSize) {
            batches.add(userSentences.subList(i, Math.min(i + batchSize, userSentences.size())));
        }
        return batches;
    }

    String buildBatchUserPrompt(List<String> sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(i + 1).append(". ").append(sentences.get(i));
        }
        return promptLoader.fillTemplate(
                promptLoader.getConversationAnalysisTemplate(),
                "conversationContent",
                sb.toString());
    }

    static GrammarAnalysisResult mergeResults(List<GrammarAnalysisResult> orderedResults) {
        List<GrammarSentenceItemDto> mergedItems = new ArrayList<>();
        if (!CollectionUtils.isEmpty(orderedResults)) {
            for (GrammarAnalysisResult result : orderedResults) {
                if (result != null && !CollectionUtils.isEmpty(result.getItems())) {
                    mergedItems.addAll(result.getItems());
                }
            }
        }
        return GrammarAnalysisResult.builder().items(mergedItems).build();
    }
}
