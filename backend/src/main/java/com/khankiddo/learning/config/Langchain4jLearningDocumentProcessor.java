package com.khankiddo.learning.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 个人文档入库预处理：
 * <ul>
 *   <li>SQL 按表拆分，且截断表后无关内容（避免 Java/笔记与 DDL 混在同一向量）</li>
 *   <li>Java 配置块单独索引（如 lockTimeOutSecond）</li>
 *   <li>其余散文用通用分块</li>
 * </ul>
 */
@Component
public class Langchain4jLearningDocumentProcessor {

    static final String METADATA_FILE_NAME = "file_name";
    static final String METADATA_DOCUMENT_TYPE = "document_type";
    static final String METADATA_TABLE_NAME = "table_name";
    static final String METADATA_SECTION = "section";

    static final String DOCUMENT_TYPE_WORK_SQL = "work-sql";
    static final String DOCUMENT_TYPE_WORK_CONFIG = "work-config";
    static final String DOCUMENT_TYPE_WORK_NOTES = "work-notes";

    private static final Pattern CREATE_TABLE_NAME =
            Pattern.compile("(?i)create\\s+table\\s+[`']?([\\w.]+)[`']?");
    private static final Pattern JAVA_FENCE =
            Pattern.compile("```java\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final DocumentSplitter proseSplitter = DocumentSplitters.recursive(800, 100);

    List<Document> expandForIndexing(Document document, String fileName, String documentType) {
        if (!DOCUMENT_TYPE_WORK_SQL.equals(documentType)) {
            return List.of(withMetadata(document.text(), fileName, documentType, null, null));
        }

        String text = document.text();
        List<Document> result = new ArrayList<>();

        // 1. Java 配置块（精确属性名检索依赖独立片段）
        Matcher javaMatcher = JAVA_FENCE.matcher(text);
        int configIndex = 0;
        while (javaMatcher.find()) {
            String javaBody = javaMatcher.group(1).trim();
            if (!StringUtils.hasText(javaBody)) {
                continue;
            }
            result.add(withMetadata(
                    javaBody,
                    fileName,
                    DOCUMENT_TYPE_WORK_CONFIG,
                    null,
                    "java-block-" + (++configIndex)));
        }

        // 2. 去掉 Java 块后再拆 SQL，避免 DDL 片段尾部带上配置代码
        String withoutJava = JAVA_FENCE.matcher(text).replaceAll("\n");

        // 3. 按表拆 SQL
        List<Document> tableDocuments = splitSqlByTable(withoutJava, fileName);
        result.addAll(tableDocuments);

        // 4. 非 SQL、非 Java 的笔记（任务表、接口说明等）
        String withoutSqlTables = removeCreateTableBlocks(withoutJava);
        if (StringUtils.hasText(withoutSqlTables) && withoutSqlTables.length() > 80) {
            result.add(withMetadata(
                    withoutSqlTables.trim(),
                    fileName,
                    DOCUMENT_TYPE_WORK_NOTES,
                    null,
                    "mixed-notes"));
        }

        if (result.isEmpty()) {
            return List.of(withMetadata(text, fileName, documentType, null, null));
        }
        return result;
    }

    List<TextSegment> splitForIngestion(Document document) {
        String documentType = document.metadata().getString(METADATA_DOCUMENT_TYPE);
        if (DOCUMENT_TYPE_WORK_SQL.equals(documentType)
                || DOCUMENT_TYPE_WORK_CONFIG.equals(documentType)) {
            return List.of(TextSegment.from(document.text(), document.metadata()));
        }
        return proseSplitter.split(document);
    }

    private List<Document> splitSqlByTable(String text, String fileName) {
        String[] segments = text.split("(?=(?i)create\\s+table\\s+)");
        List<Document> result = new ArrayList<>();
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!lower.contains("create table")) {
                continue;
            }
            String ddlOnly = trimSqlTableSegment(trimmed);
            if (!StringUtils.hasText(ddlOnly)) {
                continue;
            }
            String tableName = extractTableName(ddlOnly);
            result.add(withMetadata(ddlOnly, fileName, DOCUMENT_TYPE_WORK_SQL, tableName, null));
        }
        return result;
    }

    /**
     * 单表 DDL 片段：保留本表 create index，截断在 Markdown 小节 / Java 块之前。
     */
    private String trimSqlTableSegment(String segment) {
        int cut = segment.length();
        for (String marker : new String[] {"\n# ", "\n```java", "\n## ", "\n\n# "}) {
            int idx = segment.indexOf(marker);
            if (idx > 0) {
                cut = Math.min(cut, idx);
            }
        }
        return segment.substring(0, cut).trim();
    }

    private String removeCreateTableBlocks(String text) {
        return text.replaceAll("(?is)(?<=^|\\n)create\\s+table\\s+[\\s\\S]*?(?=\\n# |\\n## |\\n```java|$)", "")
                .replaceAll("(?is)```sql[\\s\\S]*?```", "")
                .replaceAll("(?m)^create\\s+index\\s+.*?;\\s*$", "")
                .trim();
    }

    private Document withMetadata(
            String text,
            String fileName,
            String documentType,
            String tableName,
            String section) {
        Metadata metadata = new Metadata()
                .put(METADATA_FILE_NAME, fileName)
                .put(METADATA_DOCUMENT_TYPE, documentType);
        if (StringUtils.hasText(tableName)) {
            metadata.put(METADATA_TABLE_NAME, tableName);
        }
        if (StringUtils.hasText(section)) {
            metadata.put(METADATA_SECTION, section);
        }
        return Document.from(text, metadata);
    }

    private String extractTableName(String ddlSegment) {
        Matcher matcher = CREATE_TABLE_NAME.matcher(ddlSegment);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        return "unknown-table";
    }
}
