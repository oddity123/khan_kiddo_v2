package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 分析完成后的薄弱点记忆沉淀编排：预查 → 单次 LLM → 校验落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeaknessMemoryConsolidator {

    private final WeaknessMemoryProperties properties;
    private final WeaknessMemoryContextPrefetcher prefetcher;
    private final WeaknessMemoryLlmClient llmClient;
    private final WeaknessMemoryStore store;

    public void consolidateAfterAnalysis(Long userId, String analysisId) {
        if (!properties.isEnabled()) {
            return;
        }
        if (userId == null || !StringUtils.hasText(analysisId)) {
            return;
        }
        try {
            Optional<WeaknessMemoryPrefetchContext> contextOpt = prefetcher.prefetch(userId, analysisId);
            if (contextOpt.isEmpty()) {
                log.debug("weakness consolidate skipped: no action cards analysisId={}", analysisId);
                return;
            }
            List<WeaknessMemoryOperation> operations = llmClient.consolidate(contextOpt.get());
            if (CollectionUtils.isEmpty(operations)) {
                log.debug("weakness consolidate skipped: empty LLM ops analysisId={}", analysisId);
                return;
            }
            store.applyOperations(userId, analysisId, operations);
        } catch (Exception ex) {
            log.warn("weakness consolidate failed analysisId={} userId={}: {}",
                    analysisId, userId, ex.getMessage(), ex);
        }
    }
}
