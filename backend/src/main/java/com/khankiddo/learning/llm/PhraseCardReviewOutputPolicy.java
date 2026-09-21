package com.khankiddo.learning.llm;

import com.khankiddo.learning.util.SchemaLoader;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.springframework.stereotype.Component;

/**
 * Phrase Review（中英统一）structured output：
 * 支持 json_schema 的模型走 Schema + strict；DeepSeek 等仅 JSON Object。
 */
@Component
public class PhraseCardReviewOutputPolicy {

    private static final String CACHE_SUFFIX_JSON_SCHEMA = "|phrase-json-schema";
    private static final String CACHE_SUFFIX_JSON_OBJECT = "|phrase-json-object";

    private final SchemaLoader schemaLoader;

    public PhraseCardReviewOutputPolicy(SchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    public GrammarStreamingModelSpec buildSpec(ResolvedLlmModel model) {
        if (LlmProviderSupport.supportsJsonSchemaResponseFormat(model)) {
            ResponseFormat responseFormat = StructuredJsonResponseFormat.fromClasspathSchema(
                    StructuredJsonResponseFormat.PHRASE_CARD_REVIEW_SCHEMA_NAME,
                    schemaLoader.getPhraseCardReviewSchema());
            return GrammarStreamingModelSpec.builder()
                    .cacheSuffix(CACHE_SUFFIX_JSON_SCHEMA)
                    .responseFormat(responseFormat)
                    .strictJsonSchema(true)
                    .omitMaxTokens(false)
                    .build();
        }
        return GrammarStreamingModelSpec.builder()
                .cacheSuffix(CACHE_SUFFIX_JSON_OBJECT)
                .responseFormat(StructuredJsonResponseFormat.jsonObject())
                .strictJsonSchema(false)
                .omitMaxTokens(true)
                .build();
    }

    public String composeSystemPrompt(String basePrompt, ResolvedLlmModel model) {
        if (LlmProviderSupport.supportsJsonSchemaResponseFormat(model)) {
            return basePrompt;
        }
        return basePrompt + """

                ## JSON Schema（response_format=json_object，结构由本 Schema 约束）
                只输出符合下列 Schema 的 JSON 对象：根必须是 object，键为 items（数组），不要输出顶层数组。
                不要 markdown 代码围栏、不要额外说明。

                """ + schemaLoader.getPhraseCardReviewSchema();
    }
}
