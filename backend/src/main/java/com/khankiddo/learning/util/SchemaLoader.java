package com.khankiddo.learning.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 加载 classpath 下的 JSON Schema（对话分析 / 对话分离），供 LangChain4j response_format 使用。
 */
@Slf4j
@Component
public class SchemaLoader {

    private static final String CONVERSATION_ANALYSIS_SCHEMA_PATH = "schemas/conversation-analysis-schema.json";
    private static final String CONVERSATION_SEPARATION_SCHEMA_PATH = "schemas/conversation-separation-schema.json";
    private static final String CHINESE_EXPRESSION_REVIEW_SCHEMA_PATH = "schemas/chinese-expression-review-schema.json";

    private volatile String conversationAnalysisSchema;
    private volatile String conversationSeparationSchema;
    private volatile String chineseExpressionReviewSchema;

    public String getConversationAnalysisSchema() {
        if (conversationAnalysisSchema == null) {
            conversationAnalysisSchema = load(CONVERSATION_ANALYSIS_SCHEMA_PATH);
        }
        return conversationAnalysisSchema;
    }

    public String getConversationSeparationSchema() {
        if (conversationSeparationSchema == null) {
            conversationSeparationSchema = load(CONVERSATION_SEPARATION_SCHEMA_PATH);
        }
        return conversationSeparationSchema;
    }

    public String getChineseExpressionReviewSchema() {
        if (chineseExpressionReviewSchema == null) {
            chineseExpressionReviewSchema = load(CHINESE_EXPRESSION_REVIEW_SCHEMA_PATH);
        }
        return chineseExpressionReviewSchema;
    }

    private static String load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            log.info("已加载 JSON Schema: {} ({} 字符)", path, content.length());
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("加载 JSON Schema 失败: " + path, e);
        }
    }
}
