package com.khankiddo.learning.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 仅当通义千问 API Key 已配置时启用「LangChain for Java 学习」相关 Bean，避免无 Key 时应用无法启动。
 */
public class OnQwenConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String apiKey = context.getEnvironment().getProperty("QWEN_API_KEY");
        return StringUtils.hasText(apiKey);
    }
}
