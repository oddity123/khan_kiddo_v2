package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.rag.core.QdrantEmbeddingStoreFactory;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 语法错句语义检索 {@link ContentRetriever}（Qdrant，按 userId 过滤）。
 * <p>
 * 由 {@code GrammarErrorSemanticSearchTools} 以 Tool 形式按需调用（Agentic RAG）；
 * Query 必须经 {@link GrammarRagQuerySupport#query(Long, String, Integer)} 构造，
 * userId 取自 {@code Query.metadata.chatMemoryId}。
 */
@Configuration
public class GrammarErrorSemanticRetrieverConfig {

    public static final String GRAMMAR_ERROR_SEMANTIC_CONTENT_RETRIEVER =
            "grammarErrorSemanticContentRetriever";

    @Bean(GRAMMAR_ERROR_SEMANTIC_CONTENT_RETRIEVER)
    @Conditional(OnGrammarErrorRagCondition.class)
    public ContentRetriever grammarErrorSemanticContentRetriever(
            @Qualifier(QdrantEmbeddingStoreFactory.GRAMMAR_ERROR_EMBEDDING_STORE)
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            GrammarErrorRagProperties properties) {
        int defaultMaxResults = properties.getRetrievalMaxResults();
        double minScore = properties.getRetrievalMinScore();
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .displayName("grammar-error-semantic")
                .minScore(minScore)
                .dynamicMaxResults(query -> {
                    Integer override = GrammarRagQuerySupport.maxResultsOverride(query);
                    return override != null ? override : defaultMaxResults;
                })
                .dynamicFilter(query -> MetadataFilterBuilder.metadataKey(RagMetadataKeys.USER_ID)
                        .isEqualTo(String.valueOf(GrammarRagQuerySupport.requireUserIdFromQuery(query))))
                .build();
    }
}
