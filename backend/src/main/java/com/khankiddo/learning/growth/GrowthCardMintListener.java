package com.khankiddo.learning.growth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrowthCardMintListener {

    private final GrowthCardMintGateway gateway;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMintRequested(GrowthCardMintRequestedEvent event) {
        Thread.startVirtualThread(() -> {
            try {
                gateway.mintAfterAnalysis(event.userId(), event.analysisId());
            } catch (Exception ex) {
                log.error("成长卡铸卡失败 analysisId={}", event.analysisId(), ex);
            }
        });
    }
}
