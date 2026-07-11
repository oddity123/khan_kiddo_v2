package com.khankiddo.learning.llm;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

/**
 * 将 classpath JSON Schema 转为 LangChain4j {@link ResponseFormat}（OpenAI 兼容 json_schema + strict）。
 */
public final class StructuredJsonResponseFormat {

    public static final String GRAMMAR_ANALYSIS_SCHEMA_NAME = "conversation_grammar_analysis";
    public static final String SEPARATION_SCHEMA_NAME = "conversation_separation";
    public static final String CHINESE_EXPRESSION_REVIEW_SCHEMA_NAME = "chinese_expression_review";

    private StructuredJsonResponseFormat() {
    }

    public static ResponseFormat fromClasspathSchema(String schemaName, String rawSchemaJson) {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name(schemaName)
                        .rootElement(JsonRawSchema.from(rawSchemaJson))
                        .build())
                .build();
    }
}
