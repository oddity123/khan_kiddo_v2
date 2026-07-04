package com.khankiddo.learning.rag.core;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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

    /**
     * 按 user_id + problem_types 子串过滤的向量检索（用于类型意图的精确召回）。
     */
    public List<EmbeddingMatch<TextSegment>> searchByProblemTypes(
            EmbeddingStore<TextSegment> embeddingStore,
            Long userId,
            String query,
            List<String> problemTypes,
            int maxResults,
            double minScore) {
        if (!StringUtils.hasText(query) || CollectionUtils.isEmpty(problemTypes)) {
            return Collections.emptyList();
        }
        Filter combined = Filter.and(
                userFilter(userId),
                problemTypeOrFilter(problemTypes));
        return searchWithFilter(embeddingStore, query, combined, maxResults, minScore);
    }

    private List<EmbeddingMatch<TextSegment>> searchWithFilter(
            EmbeddingStore<TextSegment> embeddingStore,
            String query,
            Filter filter,
            int maxResults,
            double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query.trim()).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();
        return embeddingStore.search(request).matches();
    }

    private Filter userFilter(Long userId) {
        return MetadataFilterBuilder.metadataKey(RagMetadataKeys.USER_ID)
                .isEqualTo(String.valueOf(userId));
    }

    private Filter problemTypeOrFilter(List<String> problemTypes) {
        List<Filter> filters = new ArrayList<>();
        for (String problemType : problemTypes) {
            if (StringUtils.hasText(problemType)) {
                filters.add(MetadataFilterBuilder.metadataKey(RagMetadataKeys.PROBLEM_TYPES)
                        .containsString(problemType.trim()));
            }
        }
        if (CollectionUtils.isEmpty(filters)) {
            throw new IllegalArgumentException("problemTypes must not be empty");
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        Filter combined = filters.get(0);
        for (int i = 1; i < filters.size(); i++) {
            combined = Filter.or(combined, filters.get(i));
        }
        return combined;
    }
}
