package com.khankiddo.learning.rag.grammar;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import org.springframework.util.StringUtils;

/**
 * 组装 LangChain4j {@link Query}：userId → chatMemoryId，可选 maxResults → InvocationParameters。
 */
public final class GrammarRagQuerySupport {

    public static final String PARAM_MAX_RESULTS = "maxResults";

    private GrammarRagQuerySupport() {
    }

    public static Query query(Long userId, String text) {
        return query(userId, text, null);
    }

    public static Query query(Long userId, String text, Integer maxResults) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("query text must not be blank");
        }
        String trimmed = text.trim();
        UserMessage userMessage = UserMessage.from(trimmed);
        InvocationParameters parameters = new InvocationParameters();
        if (maxResults != null && maxResults > 0) {
            parameters.put(PARAM_MAX_RESULTS, maxResults);
        }
        InvocationContext context = InvocationContext.builder()
                .chatMemoryId(userId)
                .invocationParameters(parameters)
                .userMessage(userMessage)
                .timestampNow()
                .build();
        Metadata metadata = Metadata.builder()
                .chatMessage(userMessage)
                .invocationContext(context)
                .build();
        return Query.from(trimmed, metadata);
    }

    public static Long requireUserIdFromQuery(Query query) {
        if (query == null || query.metadata() == null || query.metadata().chatMemoryId() == null) {
            throw new IllegalArgumentException("Query.metadata.chatMemoryId (userId) is required");
        }
        Object memoryId = query.metadata().chatMemoryId();
        if (memoryId instanceof Long longId) {
            return longId;
        }
        if (memoryId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(memoryId));
    }

    public static Integer maxResultsOverride(Query query) {
        if (query == null || query.metadata() == null || query.metadata().invocationParameters() == null) {
            return null;
        }
        Object value = query.metadata().invocationParameters().get(PARAM_MAX_RESULTS);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
