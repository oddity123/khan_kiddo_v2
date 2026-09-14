package com.khankiddo.learning.growth;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

/**
 * 切分策略选择器：MVP 一律启发式；预留按 {@code pointId} 灰度走 LLM 的出口（尚未接真调用）。
 */
@Primary
@Component
public class FocusPhraseCutSelector implements FocusPhraseCutStrategy {

    /** 后续灰度名单；空 = 全走启发式。 */
    private static final Set<String> LLM_POINT_IDS = Set.of();

    private final HeuristicFocusPhraseCutter heuristic;
    private final LlmFocusPhraseCutter llm;

    public FocusPhraseCutSelector(HeuristicFocusPhraseCutter heuristic, LlmFocusPhraseCutter llm) {
        this.heuristic = heuristic;
        this.llm = llm;
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

    private static boolean shouldUseLlm(String pointId) {
        return StringUtils.hasText(pointId) && LLM_POINT_IDS.contains(pointId.trim());
    }
}
