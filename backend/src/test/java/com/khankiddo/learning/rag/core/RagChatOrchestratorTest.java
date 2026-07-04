package com.khankiddo.learning.rag.core;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagChatOrchestratorTest {

    private final RagChatOrchestrator orchestrator = new RagChatOrchestrator();

    @Test
    void shouldIncludeStrategyAndStatsInUserPrompt() {
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                0.9,
                "id-1",
                null,
                TextSegment.from("原句: sample", Metadata.from(Map.of())));
        String prompt = invokeBuildUserPrompt(
                "对于中文的表达, 我一般会犯什么错误呢?",
                "优先检索中式英语",
                "- 中式英语 (Chinglish): 3 次",
                List.of(match),
                List.of("主类型匹配"));

        assertTrue(prompt.contains("检索策略："));
        assertTrue(prompt.contains("历史错误类型统计："));
        assertTrue(prompt.contains("检索到的历史语法错误记录："));
        assertTrue(prompt.contains("主类型匹配"));
    }

    @SuppressWarnings("unchecked")
    private String invokeBuildUserPrompt(
            String question,
            String strategy,
            String stats,
            List<EmbeddingMatch<TextSegment>> matches,
            List<String> matchLabels) {
        try {
            var method = RagChatOrchestrator.class.getDeclaredMethod(
                    "buildUserPrompt",
                    String.class,
                    String.class,
                    String.class,
                    List.class,
                    List.class);
            method.setAccessible(true);
            return (String) method.invoke(orchestrator, question, strategy, stats, matches, matchLabels);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
