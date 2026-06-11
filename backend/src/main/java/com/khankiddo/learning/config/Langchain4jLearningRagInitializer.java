package com.khankiddo.learning.config;

import com.khankiddo.learning.config.condition.OnQwenConfiguredCondition;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.llm.ResolvedLlmModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 延迟构建 RAG 检索器：首次问答时才调用千问嵌入 API 建索引，避免测试/启动阶段强依赖网络。
 */
@Slf4j
@Component
@Conditional(OnQwenConfiguredCondition.class)
@RequiredArgsConstructor
public class Langchain4jLearningRagInitializer {

    private static final String DOCS_CLASSPATH = "rag/langchain4j-learning";
    private static final String QWEN_PLUS_MODEL_ID = "qwen-plus";
    private final Langchain4jLearningProperties learningProperties;
    private final LlmModelCatalog modelCatalog;
    private final Langchain4jLearningDocumentProcessor documentProcessor;

    private volatile ContentRetriever contentRetriever;

    public List<Content> retrieve(Query query) {
        return getOrCreateContentRetriever().retrieve(query);
    }

    private ContentRetriever getOrCreateContentRetriever() {
        if (contentRetriever == null) {
            synchronized (this) {
                if (contentRetriever == null) {
                    contentRetriever = buildContentRetriever();
                }
            }
        }
        return contentRetriever;
    }

    private ContentRetriever buildContentRetriever() {
        EmbeddingModel embeddingModel = buildEmbeddingModel();
        List<Document> documents = loadAndEnrichDocuments();
        if (CollectionUtils.isEmpty(documents)) {
            throw new IllegalStateException("未找到 LangChain for Java 个人文档: classpath:" + DOCS_CLASSPATH);
        }

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(documentProcessor::splitForIngestion)
                .build()
                .ingest(documents);

        log.info(
                "LangChain for Java Easy RAG: 已索引 {} 个片段（含 SQL 按表拆分），嵌入模型={}，maxResults={}，minScore={}",
                documents.size(),
                learningProperties.getEmbeddingModelName(),
                learningProperties.getRetrievalMaxResults(),
                learningProperties.getRetrievalMinScore());

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(learningProperties.getRetrievalMaxResults())
                .minScore(learningProperties.getRetrievalMinScore())
                .build();
    }

    private EmbeddingModel buildEmbeddingModel() {
        ResolvedLlmModel qwen = modelCatalog.resolveRequired(QWEN_PLUS_MODEL_ID);
        var config = qwen.getConfig();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(modelCatalog.resolveApiKey(config))
                .modelName(learningProperties.getEmbeddingModelName())
                .maxSegmentsPerBatch(learningProperties.getEmbeddingMaxSegmentsPerBatch())
                .build();
    }

    private List<Document> loadAndEnrichDocuments() {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.txt");
        List<Document> raw = ClassPathDocumentLoader.loadDocuments(DOCS_CLASSPATH, pathMatcher);
        List<Document> enriched = new ArrayList<>();
        for (Document document : raw) {
            String fileName = resolveFileName(document);
            String documentType = resolveDocumentType(fileName, document.text());
            List<Document> expanded = documentProcessor.expandForIndexing(document, fileName, documentType);
            for (Document indexed : expanded) {
                enriched.add(indexed);
                log.info(
                        "LangChain for Java Easy RAG: 加载片段 file={} type={} table={} section={}",
                        fileName,
                        indexed.metadata().getString(Langchain4jLearningDocumentProcessor.METADATA_DOCUMENT_TYPE),
                        indexed.metadata().getString(Langchain4jLearningDocumentProcessor.METADATA_TABLE_NAME),
                        indexed.metadata().getString(Langchain4jLearningDocumentProcessor.METADATA_SECTION));
            }
        }
        return enriched;
    }

    private String resolveFileName(Document document) {
        String fileName = document.metadata().getString(Langchain4jLearningDocumentProcessor.METADATA_FILE_NAME);
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        return "unknown";
    }

    private String resolveDocumentType(String fileName, String text) {
        // 内容优先：文件名可能是 weekly-work-log，实际塞的是 DDL
        if (looksLikeSql(text)) {
            return "work-sql";
        }

        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("self-introduction") || lowerName.contains("自我介绍")) {
            return "self-introduction";
        }
        if (lowerName.contains("work-profile") || lowerName.contains("工作背景")) {
            return "work-profile";
        }
        if (lowerName.contains("current-projects") || lowerName.contains("项目")) {
            return "project";
        }
        if (lowerName.contains("weekly-work-log") || lowerName.contains("周报")) {
            return "work-log";
        }
        if (lowerName.contains("langchain") || lowerName.contains("学习笔记")) {
            return "learning-notes";
        }
        if (lowerName.contains("sql") || lowerName.contains("ddl")) {
            return "work-sql";
        }
        return "work-other";
    }

    private boolean looksLikeSql(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String sample = text.length() > 2000 ? text.substring(0, 2000).toLowerCase(Locale.ROOT) : text.toLowerCase(Locale.ROOT);
        return sample.contains("create table")
                || sample.contains("drop table")
                || sample.contains("create index")
                || sample.contains("alter table")
                || sample.contains("```sql")
                || sample.contains("comment '")
                || sample.contains("primary key");
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
