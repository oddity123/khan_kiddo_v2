package com.khankiddo.learning.weaknessmemory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 分析持久化提交后异步沉淀薄弱点记忆（虚拟线程，不阻塞铸卡/详情）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeaknessMemoryConsolidationListener {

    private final WeaknessMemoryConsolidator consolidator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConsolidationRequested(WeaknessMemoryConsolidationRequestedEvent event) {
        if (event == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                consolidator.consolidateAfterAnalysis(event.userId(), event.analysisId());
            } catch (Exception ex) {
                log.warn("weakness consolidation listener failed analysisId={}: {}",
                        event.analysisId(), ex.getMessage(), ex);
            }
        });
    }
}
