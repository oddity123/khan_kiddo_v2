package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.rag.core.QdrantClientFactory;
import com.khankiddo.learning.rag.core.RagProperties;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * 应用启动时确保语法错句 Qdrant collection 存在（LangChain4j 不会自动建表）。
 */
@Slf4j
@Component
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class GrammarErrorQdrantCollectionInitializer {

    private final QdrantClientFactory qdrantClientFactory;
    private final GrammarErrorRagProperties grammarErrorRagProperties;
    private final RagProperties ragProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureCollectionOnStartup() {
        String collectionName = grammarErrorRagProperties.getQdrant().getCollectionName();
        int dimension = ragProperties.getEmbeddingDimension();
        try (QdrantClient client = qdrantClientFactory.createClient()) {
            ensureCollection(client, collectionName, dimension);
        } catch (Exception ex) {
            log.error(
                    "语法错句 RAG: 初始化 Qdrant collection 失败 collection={} dimension={}",
                    collectionName,
                    dimension,
                    ex);
        }
    }

    static void ensureCollection(QdrantClient client, String collectionName, int dimension)
            throws ExecutionException, InterruptedException {
        Boolean exists = client.collectionExistsAsync(collectionName).get();
        if (Boolean.TRUE.equals(exists)) {
            log.info("语法错句 RAG: Qdrant collection 已存在 name={}", collectionName);
            return;
        }
        Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                .setSize(dimension)
                .setDistance(Collections.Distance.Cosine)
                .build();
        try {
            client.createCollectionAsync(collectionName, vectorParams).get();
            log.info(
                    "语法错句 RAG: 已创建 Qdrant collection name={} dimension={} distance=Cosine",
                    collectionName,
                    dimension);
        } catch (ExecutionException ex) {
            if (isAlreadyExists(ex)) {
                log.info("语法错句 RAG: Qdrant collection 已存在（并发创建）name={}", collectionName);
                return;
            }
            throw ex;
        }
    }

    private static boolean isAlreadyExists(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (!(cause instanceof StatusRuntimeException statusEx)) {
            return false;
        }
        return Status.Code.ALREADY_EXISTS.equals(statusEx.getStatus().getCode());
    }
}
