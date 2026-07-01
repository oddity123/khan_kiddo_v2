package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.rag.core.QdrantEmbeddingStoreFactory;
import com.khankiddo.learning.rag.core.RagMetadataKeys;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorIndexer {

    private static final int DELETE_SEARCH_LIMIT = 200;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final GrammarErrorDocumentBuilder documentBuilder;

    public GrammarErrorIndexer(
            EmbeddingModel embeddingModel,
            @Qualifier(QdrantEmbeddingStoreFactory.GRAMMAR_ERROR_EMBEDDING_STORE)
            EmbeddingStore<TextSegment> embeddingStore,
            GrammarErrorDocumentBuilder documentBuilder) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentBuilder = documentBuilder;
    }

    public void indexAnalysis(Long userId, String analysisId, List<ConversationAnalysisItem> items) {
        List<GrammarErrorSentenceDocument> documents = documentBuilder.groupBySentence(userId, analysisId, items);
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        List<TextSegment> segments = new ArrayList<>();
        for (GrammarErrorSentenceDocument document : documents) {
            segments.add(documentBuilder.build(document));
        }
        Response<List<Embedding>> embedded = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = embedded.content();
        for (int i = 0; i < segments.size(); i++) {
            embeddingStore.add(embeddings.get(i), segments.get(i));
        }
        log.info("语法错句 RAG: 已索引 analysisId={} 句子数={}", analysisId, segments.size());
    }

    public void removeByAnalysisId(Long userId, String analysisId, List<Long> sentenceIds) {
        if (CollectionUtils.isEmpty(sentenceIds)) {
            return;
        }
        for (Long sentenceId : sentenceIds) {
            removeSentence(userId, analysisId, sentenceId);
        }
        log.info("语法错句 RAG: 已删除 analysisId={} 向量数={}", analysisId, sentenceIds.size());
    }

    private void removeSentence(Long userId, String analysisId, Long sentenceId) {
        Filter filter = Filter.and(
                Filter.and(
                        MetadataFilterBuilder.metadataKey(RagMetadataKeys.USER_ID).isEqualTo(String.valueOf(userId)),
                        MetadataFilterBuilder.metadataKey(RagMetadataKeys.ANALYSIS_ID).isEqualTo(analysisId)),
                MetadataFilterBuilder.metadataKey(RagMetadataKeys.SENTENCE_ID).isEqualTo(String.valueOf(sentenceId)));
        List<String> ids = findEmbeddingIds(filter);
        if (!CollectionUtils.isEmpty(ids)) {
            embeddingStore.removeAll(ids);
        }
    }

    private List<String> findEmbeddingIds(Filter filter) {
        Embedding dummyQuery = embeddingModel.embed("grammar-error-delete").content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(dummyQuery)
                .maxResults(DELETE_SEARCH_LIMIT)
                .minScore(0.0)
                .filter(filter)
                .build();
        return embeddingStore.search(request).matches().stream()
                .map(EmbeddingMatch::embeddingId)
                .toList();
    }
}
