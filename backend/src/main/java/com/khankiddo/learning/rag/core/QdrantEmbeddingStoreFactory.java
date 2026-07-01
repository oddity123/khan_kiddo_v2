package com.khankiddo.learning.rag.core;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.rag.grammar.GrammarErrorRagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class QdrantEmbeddingStoreFactory {

    public static final String GRAMMAR_ERROR_EMBEDDING_STORE = "grammarErrorEmbeddingStore";

    private final GrammarErrorRagProperties grammarErrorRagProperties;
    private final QdrantClientFactory qdrantClientFactory;

    @Bean(GRAMMAR_ERROR_EMBEDDING_STORE)
    public EmbeddingStore<TextSegment> grammarErrorEmbeddingStore() {
        GrammarErrorRagProperties.Qdrant qdrant = grammarErrorRagProperties.getQdrant();
        return QdrantEmbeddingStore.builder()
                .client(qdrantClientFactory.createClient())
                .collectionName(qdrant.getCollectionName())
                .build();
    }
}
