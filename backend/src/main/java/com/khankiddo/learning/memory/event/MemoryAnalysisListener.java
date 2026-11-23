package com.khankiddo.learning.memory.event;

import com.khankiddo.learning.memory.assessment.MemoryAssessmentService;
import com.khankiddo.learning.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务提交后异步编排：
 * 1) 分析完成 → 确定性 HIT 沉淀 → 单次 LLM 回检评估 → Mastery 更新；
 * 2) 分析删除 → 清理派生 Evidence/Session 并重算记忆状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAnalysisListener {

    private final MemoryService memoryService;
    private final MemoryAssessmentService assessmentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCompleted(MemoryAnalysisCompletedEvent event) {
        if (event == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                memoryService.recordAnalysisHits(event.userId(), event.analysisId());
            } catch (Exception ex) {
                log.warn("memory recordAnalysisHits failed analysisId={}: {}",
                        event.analysisId(), ex.getMessage(), ex);
            }
            try {
                assessmentService.assessAfterAnalysis(
                        event.userId(), event.analysisId(), event.sourceConversationId());
            } catch (Exception ex) {
                log.warn("memory assessment failed analysisId={}: {}",
                        event.analysisId(), ex.getMessage(), ex);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisDeleted(MemoryAnalysisDeletedEvent event) {
        if (event == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                memoryService.handleAnalysisDeleted(event.userId(), event.analysisId());
            } catch (Exception ex) {
                log.warn("memory cleanup failed analysisId={}: {}",
                        event.analysisId(), ex.getMessage(), ex);
            }
        });
    }
}
