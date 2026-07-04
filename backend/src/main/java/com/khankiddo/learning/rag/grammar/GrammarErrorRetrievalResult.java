package com.khankiddo.learning.rag.grammar;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

/**
 * Agent Router 检索结果：意图、策略说明、统计摘要与重排后的片段。
 */
public record GrammarErrorRetrievalResult(
        GrammarErrorRetrievalIntent intent,
        String retrievalStrategy,
        String statsSummary,
        List<EmbeddingMatch<TextSegment>> matches,
        List<String> matchLabels) {
}
