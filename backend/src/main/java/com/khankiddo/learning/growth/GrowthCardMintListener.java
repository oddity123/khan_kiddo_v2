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

    /**
     * 词汇卡只落库、无 LLM。必须在当前线程完成后再让分析请求返回，
     * 否则详情页会先于铸卡加载，卡片库为空。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMintRequested(GrowthCardMintRequestedEvent event) {
        try {
            gateway.mintAfterAnalysis(event.userId(), event.analysisId());
        } catch (Exception ex) {
            log.error("成长卡铸卡失败 analysisId={}", event.analysisId(), ex);
        }
    }
}
