package com.khankiddo.learning.conversation;

import com.khankiddo.learning.ai.conversation.model.GrammarAnalysisResult;
import com.khankiddo.learning.ai.conversation.model.GrammarSentenceItemDto;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 用户句数超过阈值时，按 {@link ConversationAnalysisProperties#getBatchSize()} 均分切批并发分析。
 * 分批一律非流式（chat），不占用 Stage 2 流式连接。
 * 失败或尚未开始的批次再试一次；已成功批次保留。并发上限沿用配置，不在此降低。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationBatchGrammarAnalyzer {

    private static final int FAILED_BATCH_MAX_ATTEMPTS =
            ConversationAnalysisTimeoutBudget.FAILED_BATCH_MAX_ATTEMPTS;

    private final ConversationAnalysisStreamingHelper streamingHelper;
    private final GrammarAnalysisUserPromptBuilder userPromptBuilder;
    private final ConversationAnalysisProperties properties;

    public GrammarAnalysisResult analyzeInBatches(
            List<String> userSentences,
            String systemPrompt,
            ResolvedLlmModel model,
            Consumer<ConversationAnalysisProgress> onProgress) {
        return analyzeInBatches(userSentences, systemPrompt, model, null, onProgress);
    }

    public GrammarAnalysisResult analyzeInBatches(
            List<String> userSentences,
            String systemPrompt,
            ResolvedLlmModel model,
            String analysisId,
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

        Consumer<ConversationAnalysisProgress> batchProgress =
                ConversationAnalysisProgressRelay.synchronizedBatchLevelSink(onProgress);
        Semaphore semaphore = new Semaphore(properties.getBatchConcurrentLimit());
        AtomicInteger completedCount = new AtomicInteger(0);
        List<GrammarAnalysisResult> orderedResults =
                Collections.synchronizedList(new ArrayList<>(Collections.nCopies(totalBatches, null)));
        Throwable[] lastErrors = new Throwable[totalBatches];

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            runWave(executor, batches, systemPrompt, model, analysisId, batchProgress,
                    semaphore, completedCount, orderedResults, lastErrors, 1, true);
            List<Integer> missing = missingBatchIndexes(orderedResults);
            if (!CollectionUtils.isEmpty(missing) && FAILED_BATCH_MAX_ATTEMPTS > 1) {
                log.info("分批语法分析准备重试失败或未开始批次 analysisId={}, indexes={}",
                        analysisId, missing.stream().map(i -> i + 1).toList());
                runWave(executor, batches, systemPrompt, model, analysisId, batchProgress,
                        semaphore, completedCount, orderedResults, lastErrors, 2, false, missing);
            }
        }

        List<Integer> stillMissing = missingBatchIndexes(orderedResults);
        if (!CollectionUtils.isEmpty(stillMissing)) {
            int failedNum = stillMissing.getFirst() + 1;
            Throwable lastError = lastErrors[stillMissing.getFirst()];
            log.warn("分批语法分析仍有失败批次 analysisId={}, batchNum={}/{}",
                    analysisId, failedNum, totalBatches, lastError);
            throw toBatchFailure(lastError);
        }

        return mergeResults(orderedResults);
    }

    private void runWave(
            ExecutorService executor,
            List<List<String>> batches,
            String systemPrompt,
            ResolvedLlmModel model,
            String analysisId,
            Consumer<ConversationAnalysisProgress> batchProgress,
            Semaphore semaphore,
            AtomicInteger completedCount,
            List<GrammarAnalysisResult> orderedResults,
            Throwable[] lastErrors,
            int attempt,
            boolean cancelUnstartedOnFailure) {
        runWave(executor, batches, systemPrompt, model, analysisId, batchProgress,
                semaphore, completedCount, orderedResults, lastErrors, attempt,
                cancelUnstartedOnFailure, allIndexes(batches.size()));
    }

    private void runWave(
            ExecutorService executor,
            List<List<String>> batches,
            String systemPrompt,
            ResolvedLlmModel model,
            String analysisId,
            Consumer<ConversationAnalysisProgress> batchProgress,
            Semaphore semaphore,
            AtomicInteger completedCount,
            List<GrammarAnalysisResult> orderedResults,
            Throwable[] lastErrors,
            int attempt,
            boolean cancelUnstartedOnFailure,
            List<Integer> batchIndexes) {

        int totalBatches = batches.size();
        AtomicBoolean cancelUnstarted = new AtomicBoolean(false);
        List<Future<?>> futures = new ArrayList<>();
        for (int batchIndex : batchIndexes) {
            final int index = batchIndex;
            final int batchNum = batchIndex + 1;
            final List<String> batchSentences = batches.get(batchIndex);
            futures.add(executor.submit(() -> {
                runOneBatch(systemPrompt, model, analysisId, batchProgress, semaphore, completedCount,
                        orderedResults, lastErrors, attempt, cancelUnstartedOnFailure, cancelUnstarted,
                        totalBatches, index, batchNum, batchSentences);
                return null;
            }));
        }
        awaitAll(futures, cancelUnstarted);
    }

    private void runOneBatch(
            String systemPrompt,
            ResolvedLlmModel model,
            String analysisId,
            Consumer<ConversationAnalysisProgress> batchProgress,
            Semaphore semaphore,
            AtomicInteger completedCount,
            List<GrammarAnalysisResult> orderedResults,
            Throwable[] lastErrors,
            int attempt,
            boolean cancelUnstartedOnFailure,
            AtomicBoolean cancelUnstarted,
            int totalBatches,
            int index,
            int batchNum,
            List<String> batchSentences) {

        if (cancelUnstarted.get()) {
            log.info("分批语法分析跳过未开始批次 analysisId={}, batchNum={}/{}, attempt={}",
                    analysisId, batchNum, totalBatches, attempt);
            return;
        }
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            cancelUnstarted.set(true);
            throw new BadRequestException("分批分析被中断");
        }
        try {
            if (cancelUnstarted.get()) {
                log.info("分批语法分析跳过未开始批次 analysisId={}, batchNum={}/{}, attempt={}",
                        analysisId, batchNum, totalBatches, attempt);
                return;
            }
            log.info("分批语法分析开始 analysisId={}, batchNum={}/{}, attempt={}, sentences={}",
                    analysisId, batchNum, totalBatches, attempt, batchSentences.size());
            String userPrompt = userPromptBuilder.buildFromUserSentences(batchSentences);
            GrammarAnalysisResult result = streamingHelper.analyzeGrammarWithoutStreaming(
                    systemPrompt, userPrompt, model, batchNum, totalBatches, batchProgress);
            orderedResults.set(index, result);
            lastErrors[index] = null;
            int done = completedCount.incrementAndGet();
            batchProgress.accept(ConversationAnalysisProgress.builder()
                    .status(ConversationAnalysisProgress.STATUS_ANALYZING)
                    .message("已完成 " + done + "/" + totalBatches + " 批")
                    .build());
            log.info("分批语法分析第 {}/{} 批完成 analysisId={}, attempt={}, 本批 {} 句",
                    batchNum, totalBatches, analysisId, attempt, batchSentences.size());
        } catch (RuntimeException ex) {
            lastErrors[index] = ex;
            if (cancelUnstartedOnFailure) {
                cancelUnstarted.set(true);
            }
            log.warn("分批语法分析失败 analysisId={}, batchNum={}/{}, attempt={}: {}",
                    analysisId, batchNum, totalBatches, attempt, ex.toString());
        } finally {
            semaphore.release();
        }
    }

    private static void awaitAll(List<Future<?>> futures, AtomicBoolean cancelUnstarted) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                cancelUnstarted.set(true);
                cancelRemaining(futures);
                throw new BadRequestException("分批分析被中断");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof BadRequestException badRequest
                        && "分批分析被中断".equals(badRequest.getMessage())) {
                    cancelUnstarted.set(true);
                    cancelRemaining(futures);
                    throw badRequest;
                }
                throw toBatchFailure(cause);
            }
        }
    }

    private static void cancelRemaining(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    private static BadRequestException toBatchFailure(Throwable cause) {
        if (cause instanceof BadRequestException badRequest) {
            String userMessage = ConversationAnalysisErrorMessages.toUserMessage(badRequest);
            if (badRequest.getMessage() != null && badRequest.getMessage().equals(userMessage)) {
                return badRequest;
            }
            return new BadRequestException(userMessage);
        }
        return new BadRequestException(ConversationAnalysisErrorMessages.toUserMessage(cause));
    }

    private static List<Integer> allIndexes(int totalBatches) {
        List<Integer> indexes = new ArrayList<>(totalBatches);
        for (int i = 0; i < totalBatches; i++) {
            indexes.add(i);
        }
        return indexes;
    }

    private static List<Integer> missingBatchIndexes(List<GrammarAnalysisResult> orderedResults) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < orderedResults.size(); i++) {
            if (orderedResults.get(i) == null) {
                missing.add(i);
            }
        }
        return missing;
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
