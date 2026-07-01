package com.khankiddo.learning.rag.core;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 按 user_id 隔离的向量检索。
 */
@Component
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class UserScopedVectorRetriever {

    private final EmbeddingModel embeddingModel;

    public List<EmbeddingMatch<TextSegment>> search(
            EmbeddingStore<TextSegment> embeddingStore,
            Long userId,
            String query,
            int maxResults,
            double minScore) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        Embedding queryEmbedding = embeddingModel.embed(query.trim()).content();
        Filter userFilter = MetadataFilterBuilder.metadataKey(RagMetadataKeys.USER_ID)
                .isEqualTo(String.valueOf(userId));
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(userFilter)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return result.matches();
    }
}
