package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.rag.core.RagMetadataKeys;
import com.khankiddo.learning.rag.core.UserScopedVectorRetriever;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarErrorRetrievalServiceTest {

    @Mock
    private UserScopedVectorRetriever vectorRetriever;
    @Mock
    private GrammarErrorStatsService statsService;
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    private GrammarErrorRagProperties properties;
    private GrammarErrorRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        properties = new GrammarErrorRagProperties();
        properties.setRetrievalMaxResults(4);
        properties.setRetrievalCandidatePoolSize(10);
        properties.setRetrievalMaxSecondaryResults(2);
        properties.setRetrievalMinScore(0.5);
        retrievalService = new GrammarErrorRetrievalService(
                vectorRetriever,
                properties,
                new GrammarErrorQueryIntentAnalyzer(),
                new GrammarErrorHybridRanker(),
                statsService,
                embeddingStore);
    }

    @Test
    void shouldRouteChinglishQuestionThroughTwoStageRetrieval() {
        EmbeddingMatch<TextSegment> chinglishMatch = match(0.78, "Collocation,Chinglish", "take some plan");
        EmbeddingMatch<TextSegment> structureMatch = match(0.82, "Structure", "you repeat that again");
        EmbeddingMatch<TextSegment> unnaturalMatch = match(0.80, "Unnatural", "awkward expression");

        when(vectorRetriever.search(any(), eq(1L), anyString(), eq(10), eq(0.5)))
                .thenReturn(List.of(structureMatch, unnaturalMatch, chinglishMatch));
        when(vectorRetriever.searchByProblemTypes(any(), eq(1L), anyString(), anyList(), eq(10), eq(0.0)))
                .thenReturn(List.of(chinglishMatch));
        when(statsService.buildStatsSummary(anyLong(), any())).thenReturn("- 中式英语 (Chinglish): 3 次");

        GrammarErrorRetrievalResult result = retrievalService.retrieveForChat(
                1L, "中文表达中我一般犯的错误是哪些呢");

        verify(vectorRetriever).searchByProblemTypes(
                eq(embeddingStore), eq(1L), anyString(), anyList(), eq(10), eq(0.0));
        assertEquals(GrammarErrorQueryKind.CHINGLISH, result.intent().kind());
        assertNotNull(result.statsSummary());
        assertEquals("主类型匹配", result.matchLabels().get(0));
        assertTrue(result.matches().get(0).embedded().text().contains("take some plan"));
        long secondaryCount = result.matchLabels().stream().filter("补充参考"::equals).count();
        assertTrue(secondaryCount <= 2);
    }

    private EmbeddingMatch<TextSegment> match(double score, String problemTypes, String text) {
        Metadata metadata = Metadata.from(Map.of(RagMetadataKeys.PROBLEM_TYPES, problemTypes));
        TextSegment segment = TextSegment.from(text, metadata);
        return new EmbeddingMatch<>(score, "id-" + score + "-" + problemTypes, null, segment);
    }
}
