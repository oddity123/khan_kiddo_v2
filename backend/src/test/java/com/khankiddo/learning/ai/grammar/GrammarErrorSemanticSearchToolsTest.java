package com.khankiddo.learning.ai.grammar;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarErrorSemanticSearchToolsTest {

    @Mock
    private ObjectProvider<ContentRetriever> retrieverProvider;

    @Mock
    private ContentRetriever retriever;

    @Test
    void search_shouldBuildUserScopedQuery() {
        when(retrieverProvider.getIfAvailable()).thenReturn(retriever);
        when(retriever.retrieve(any())).thenReturn(List.of(Content.from("I go to school yesterday. → 时态错误")));
        GrammarErrorSemanticSearchTools tools = new GrammarErrorSemanticSearchTools(retrieverProvider);

        String result = tools.searchSimilarGrammarErrors(7L, "过去式", 3);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(retriever).retrieve(captor.capture());
        assertEquals(7L, captor.getValue().metadata().chatMemoryId());
        assertEquals("过去式", captor.getValue().text());
        assertTrue(result.contains("I go to school yesterday"));
    }

    @Test
    void search_shouldHintWhenRetrieverAbsent() {
        when(retrieverProvider.getIfAvailable()).thenReturn(null);
        GrammarErrorSemanticSearchTools tools = new GrammarErrorSemanticSearchTools(retrieverProvider);

        String result = tools.searchSimilarGrammarErrors(7L, "过去式", null);

        assertTrue(result.contains("未启用"));
    }

    @Test
    void search_shouldRejectBlankQuery() {
        when(retrieverProvider.getIfAvailable()).thenReturn(retriever);
        GrammarErrorSemanticSearchTools tools = new GrammarErrorSemanticSearchTools(retrieverProvider);

        String result = tools.searchSimilarGrammarErrors(7L, "  ", null);

        assertTrue(result.contains("不能为空"));
    }

    @Test
    void formatContents_shouldReportEmpty() {
        assertTrue(GrammarErrorSemanticSearchTools.formatContents(List.of()).contains("未检索到"));
    }

    @Test
    void normalizeMaxResults_shouldClampToLimit() {
        assertNull(GrammarErrorSemanticSearchTools.normalizeMaxResults(null));
        assertNull(GrammarErrorSemanticSearchTools.normalizeMaxResults(0));
        assertEquals(10, GrammarErrorSemanticSearchTools.normalizeMaxResults(99));
        assertEquals(5, GrammarErrorSemanticSearchTools.normalizeMaxResults(5));
    }
}
