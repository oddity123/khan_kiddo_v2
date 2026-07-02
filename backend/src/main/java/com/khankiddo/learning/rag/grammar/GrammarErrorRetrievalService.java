package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.rag.core.QdrantEmbeddingStoreFactory;
import com.khankiddo.learning.rag.core.UserScopedVectorRetriever;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorRetrievalService {

    private final UserScopedVectorRetriever vectorRetriever;
    private final GrammarErrorRagProperties properties;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public GrammarErrorRetrievalService(
            UserScopedVectorRetriever vectorRetriever,
            GrammarErrorRagProperties properties,
            @Qualifier(QdrantEmbeddingStoreFactory.GRAMMAR_ERROR_EMBEDDING_STORE)
            EmbeddingStore<TextSegment> embeddingStore) {
        this.vectorRetriever = vectorRetriever;
        this.properties = properties;
        this.embeddingStore = embeddingStore;
    }

    public List<EmbeddingMatch<TextSegment>> retrieveForChat(Long userId, String query) {
        return vectorRetriever.search(
                embeddingStore,
                userId,
                query,
                properties.getRetrievalMaxResults(),
                properties.getRetrievalMinScore());
    }
}
