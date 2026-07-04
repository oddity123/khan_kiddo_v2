package com.khankiddo.learning.rag.grammar;

import java.util.List;

/**
 * 检索意图：错误类型优先级、关键词扩展、是否附带统计。
 */
public record GrammarErrorRetrievalIntent(
        GrammarErrorQueryKind kind,
        List<String> primaryTypes,
        List<String> secondaryTypes,
        List<String> expandedKeywords,
        boolean includeStats) {

    public static GrammarErrorRetrievalIntent semanticDefault() {
        return new GrammarErrorRetrievalIntent(
                GrammarErrorQueryKind.SEMANTIC,
                List.of(),
                List.of(),
                List.of(),
                false);
    }
}
