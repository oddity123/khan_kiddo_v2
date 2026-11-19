package com.khankiddo.learning.weaknessmemory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 分析删除后清理仍以该 analysis 为 source 的薄弱点记忆。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeaknessMemoryCleanupListener {

    private final WeaknessMemoryStore store;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleanupRequested(WeaknessMemoryCleanupRequestedEvent event) {
        if (event == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                int deleted = store.deleteBySourceAnalysis(event.userId(), event.analysisId());
                if (deleted > 0) {
                    log.info("weakness memories cleaned userId={} analysisId={} count={}",
                            event.userId(), event.analysisId(), deleted);
                }
            } catch (Exception ex) {
                log.warn("weakness cleanup listener failed analysisId={}: {}",
                        event.analysisId(), ex.getMessage(), ex);
            }
        });
    }
}
