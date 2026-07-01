package com.khankiddo.learning.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 语法错句 RAG：需同时配置千问嵌入 API 与 Qdrant 地址。
 */
public class OnGrammarErrorRagCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String qwenKey = context.getEnvironment().getProperty("QWEN_API_KEY");
        String qdrantHost = context.getEnvironment().getProperty("QDRANT_HOST");
        if (!StringUtils.hasText(qdrantHost)) {
            qdrantHost = context.getEnvironment().getProperty("app.grammar-error-rag.qdrant.host");
        }
        return StringUtils.hasText(qwenKey) && StringUtils.hasText(qdrantHost);
    }
}
