package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class GrammarErrorIndexListener {

    private final GrammarErrorIndexer indexer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIndexed(GrammarErrorIndexedEvent event) {
        Thread.startVirtualThread(() -> {
            try {
                indexer.indexAnalysis(event.userId(), event.analysisId(), event.items());
            } catch (Exception ex) {
                log.error("语法错句 RAG 索引失败 analysisId={}", event.analysisId(), ex);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeleted(GrammarErrorDeletedEvent event) {
        Thread.startVirtualThread(() -> {
            try {
                indexer.removeByAnalysisId(event.userId(), event.analysisId(), event.sentenceIds());
            } catch (Exception ex) {
                log.error("语法错句 RAG 删除向量失败 analysisId={}", event.analysisId(), ex);
            }
        });
    }
}
