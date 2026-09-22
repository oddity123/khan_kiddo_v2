package com.khankiddo.learning.config.condition;

import org.junit.jupiter.api.Test;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.annotation.ConditionContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnGrammarErrorRagConditionTest {

    private final OnGrammarErrorRagCondition condition = new OnGrammarErrorRagCondition();
    private final AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

    @Test
    void matches_whenEnabledAndKeyAndHostPresent() {
        assertTrue(condition.matches(context(
                "true", "qwen-key", "127.0.0.1"), metadata));
    }

    @Test
    void matches_whenEnabledOmitted_defaultsTrue() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("QWEN_API_KEY", "qwen-key");
        env.setProperty("QDRANT_HOST", "127.0.0.1");
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        assertTrue(condition.matches(ctx, metadata));
    }

    @Test
    void doesNotMatch_whenEnabledFalse() {
        assertFalse(condition.matches(context(
                "false", "qwen-key", "127.0.0.1"), metadata));
    }

    @Test
    void doesNotMatch_whenKeyMissing() {
        assertFalse(condition.matches(context(
                "true", "", "127.0.0.1"), metadata));
    }

    @Test
    void doesNotMatch_whenHostMissing() {
        assertFalse(condition.matches(context(
                "true", "qwen-key", ""), metadata));
    }

    @Test
    void matches_whenHostFromYamlProperty() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.grammar-error-rag.enabled", "true");
        env.setProperty("QWEN_API_KEY", "qwen-key");
        env.setProperty("app.grammar-error-rag.qdrant.host", "qdrant.local");
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        assertTrue(condition.matches(ctx, metadata));
    }

    private static ConditionContext context(String enabled, String qwenKey, String qdrantHost) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.grammar-error-rag.enabled", enabled);
        if (qwenKey != null && !qwenKey.isEmpty()) {
            env.setProperty("QWEN_API_KEY", qwenKey);
        }
        if (qdrantHost != null && !qdrantHost.isEmpty()) {
            env.setProperty("QDRANT_HOST", qdrantHost);
        }
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        return ctx;
    }
}
