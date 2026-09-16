package com.khankiddo.learning.growth;

import com.khankiddo.learning.config.FocusPhraseCutProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 切分策略选择器：MVP 一律启发式；可按 {@code app.focus-phrase-cut.llm-point-ids} 灰度走 LLM。
 */
@Primary
@Component
public class FocusPhraseCutSelector implements FocusPhraseCutStrategy {

    private final HeuristicFocusPhraseCutter heuristic;
    private final LlmFocusPhraseCutter llm;
    private final Set<String> llmPointIds;

    public FocusPhraseCutSelector(
            HeuristicFocusPhraseCutter heuristic,
            LlmFocusPhraseCutter llm,
            FocusPhraseCutProperties properties) {
        this.heuristic = heuristic;
        this.llm = llm;
        this.llmPointIds = normalizePointIds(properties != null ? properties.getLlmPointIds() : null);
    }

    @Override
    public Optional<FocusPhrasePair> cut(FocusPhraseCutRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        if (shouldUseLlm(request.pointId())) {
            Optional<FocusPhrasePair> llmResult = llm.cut(request);
            if (llmResult.isPresent()) {
                return llmResult;
            }
            // LLM 未就绪或失败时回退启发式
        }
        return heuristic.cut(request);
    }

    private boolean shouldUseLlm(String pointId) {
        return StringUtils.hasText(pointId) && llmPointIds.contains(pointId.trim());
    }

    private static Set<String> normalizePointIds(java.util.List<String> raw) {
        if (CollectionUtils.isEmpty(raw)) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (String id : raw) {
            if (StringUtils.hasText(id)) {
                ids.add(id.trim());
            }
        }
        return Set.copyOf(ids);
    }
}
