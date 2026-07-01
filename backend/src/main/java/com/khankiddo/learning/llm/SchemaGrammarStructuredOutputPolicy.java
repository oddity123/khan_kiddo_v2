package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.util.SchemaLoader;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 默认策略：非千问模型沿用 json_schema + strict（可被配置关闭）。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SchemaGrammarStructuredOutputPolicy implements GrammarStructuredOutputPolicy {

    private static final String CACHE_SUFFIX_GRAMMAR_SCHEMA = "|grammar-json-schema";

    private final ConversationAnalysisProperties conversationAnalysisProperties;
    private final SchemaLoader schemaLoader;

    public SchemaGrammarStructuredOutputPolicy(
            ConversationAnalysisProperties conversationAnalysisProperties,
            SchemaLoader schemaLoader) {
        this.conversationAnalysisProperties = conversationAnalysisProperties;
        this.schemaLoader = schemaLoader;
    }

    @Override
    public boolean supports(ResolvedLlmModel model) {
        return true;
    }

    @Override
    public GrammarStreamingModelSpec buildSpec(ResolvedLlmModel model) {
        if (!conversationAnalysisProperties.isStrictJsonSchema()) {
            return GrammarStreamingModelSpec.builder()
                    .cacheSuffix("")
                    .responseFormat(null)
                    .strictJsonSchema(false)
                    .omitMaxTokens(false)
                    .build();
        }
        ResponseFormat responseFormat = StructuredJsonResponseFormat.fromClasspathSchema(
                StructuredJsonResponseFormat.GRAMMAR_ANALYSIS_SCHEMA_NAME,
                schemaLoader.getConversationAnalysisSchema());
        return GrammarStreamingModelSpec.builder()
                .cacheSuffix(CACHE_SUFFIX_GRAMMAR_SCHEMA)
                .responseFormat(responseFormat)
                .strictJsonSchema(true)
                .omitMaxTokens(false)
                .build();
    }
}
