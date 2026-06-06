package com.khankiddo.learning.util;

import com.khankiddo.learning.llm.StructuredJsonResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaLoaderTest {

    @Test
    void loadsConversationAnalysisSchemaAndBuildsResponseFormat() {
        SchemaLoader loader = new SchemaLoader();
        String schema = loader.getConversationAnalysisSchema();
        assertThat(schema).contains("\"items\"").contains("originalSentence");

        ResponseFormat format = StructuredJsonResponseFormat.fromClasspathSchema(
                StructuredJsonResponseFormat.GRAMMAR_ANALYSIS_SCHEMA_NAME, schema);
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.jsonSchema().name()).isEqualTo(StructuredJsonResponseFormat.GRAMMAR_ANALYSIS_SCHEMA_NAME);
        assertThat(format.jsonSchema().rootElement()).isInstanceOf(JsonRawSchema.class);
    }
}
