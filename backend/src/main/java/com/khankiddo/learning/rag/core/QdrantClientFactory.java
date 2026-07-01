package com.khankiddo.learning.rag.core;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.rag.grammar.GrammarErrorRagProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 构建与 LangChain4j {@link dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore} 一致的 gRPC 客户端。
 */
@Component
@Conditional(OnGrammarErrorRagCondition.class)
public class QdrantClientFactory {

    private final GrammarErrorRagProperties grammarErrorRagProperties;

    public QdrantClientFactory(GrammarErrorRagProperties grammarErrorRagProperties) {
        this.grammarErrorRagProperties = grammarErrorRagProperties;
    }

    public QdrantClient createClient() {
        GrammarErrorRagProperties.Qdrant qdrant = grammarErrorRagProperties.getQdrant();
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                qdrant.getHost().trim(),
                qdrant.getPort(),
                qdrant.isUseTls());
        if (StringUtils.hasText(qdrant.getApiKey())) {
            builder.withApiKey(qdrant.getApiKey().trim());
        }
        return new QdrantClient(builder.build());
    }
}
