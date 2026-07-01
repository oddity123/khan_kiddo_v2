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
import org.springframework.util.StringUtils;

@Configuration
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class QdrantEmbeddingStoreFactory {

    public static final String GRAMMAR_ERROR_EMBEDDING_STORE = "grammarErrorEmbeddingStore";

    private final GrammarErrorRagProperties grammarErrorRagProperties;
    private final RagProperties ragProperties;

    @Bean(GRAMMAR_ERROR_EMBEDDING_STORE)
    public EmbeddingStore<TextSegment> grammarErrorEmbeddingStore() {
        GrammarErrorRagProperties.Qdrant qdrant = grammarErrorRagProperties.getQdrant();
        var builder = QdrantEmbeddingStore.builder()
                .host(qdrant.getHost().trim())
                .port(qdrant.getPort())
                .collectionName(qdrant.getCollectionName())
                .useTls(qdrant.isUseTls());
        if (StringUtils.hasText(qdrant.getApiKey())) {
            builder.apiKey(qdrant.getApiKey().trim());
        }
        return builder.build();
    }
}
