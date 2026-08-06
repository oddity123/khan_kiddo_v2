package com.khankiddo.learning.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.llm.StructuredJsonResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

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

    @Test
    void errorSchemaUsesPointIdInsteadOfType() throws Exception {
        JsonNode errorProperties = errorItemNode(new SchemaLoader()).path("properties");

        assertThat(errorProperties.has("pointId")).isTrue();
        assertThat(errorProperties.has("point")).isTrue();
        assertThat(errorProperties.has("type")).isFalse();

        List<String> required = toStringList(errorItemNode(new SchemaLoader()).path("required"));
        assertThat(required).containsExactlyInAnyOrder("pointId", "point");
    }

    @Test
    void errorSchemaPointIdEnumMatchesDictionary() throws Exception {
        JsonNode pointIdEnum = errorItemNode(new SchemaLoader()).path("properties").path("pointId").path("enum");
        List<String> enumValues = toStringList(pointIdEnum);

        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        assertThat(enumValues).containsExactlyInAnyOrderElementsOf(dictionary.allPointIds());
    }

    private static JsonNode errorItemNode(SchemaLoader loader) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(loader.getConversationAnalysisSchema());
        return root.path("properties").path("items").path("items")
                .path("properties").path("errors").path("items");
    }

    private static List<String> toStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        Spliterator<JsonNode> spliterator = Spliterators.spliteratorUnknownSize(arrayNode.elements(), 0);
        StreamSupport.stream(spliterator, false).forEach(node -> values.add(node.asText()));
        return values;
    }
}
