package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import com.khankiddo.learning.util.SchemaLoader;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseExpressionReviewOutputPolicyTest {

    private ChineseExpressionReviewOutputPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ChineseExpressionReviewOutputPolicy(new SchemaLoader());
    }

    @Test
    void deepseek_usesJsonObjectWithoutSchema() {
        GrammarStreamingModelSpec spec = policy.buildSpec(deepseek());

        assertThat(spec.getResponseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(spec.getResponseFormat().jsonSchema()).isNull();
        assertThat(spec.isStrictJsonSchema()).isFalse();
        assertThat(spec.isOmitMaxTokens()).isTrue();
        assertThat(spec.getCacheSuffix()).contains("json-object");
    }

    @Test
    void deepseek_promptInjectsObjectSchemaNotBareArray() {
        String composed = policy.composeSystemPrompt("BASE JSON", deepseek());

        assertThat(composed).startsWith("BASE JSON");
        assertThat(composed).contains("json_object");
        assertThat(composed).contains("不要输出顶层数组");
        assertThat(composed).contains("\"items\"");
        assertThat(composed).contains("focusPhrase");
    }

    @Test
    void doubao_keepsJsonSchemaStrict() {
        GrammarStreamingModelSpec spec = policy.buildSpec(doubao());

        assertThat(spec.getResponseFormat().jsonSchema()).isNotNull();
        assertThat(spec.getResponseFormat().jsonSchema().name())
                .isEqualTo(StructuredJsonResponseFormat.CHINESE_EXPRESSION_REVIEW_SCHEMA_NAME);
        assertThat(spec.isStrictJsonSchema()).isTrue();
        assertThat(policy.composeSystemPrompt("BASE", doubao())).isEqualTo("BASE");
    }

    private static ResolvedLlmModel deepseek() {
        return model("deepseek-v4-flash-0731", "qwen",
                "deepseek-v4-flash-0731",
                "https://dashscope.aliyuncs.com/compatible-mode/v1");
    }

    private static ResolvedLlmModel doubao() {
        return model("doubao-seed", "doubao",
                "doubao-seed-1-8-251228",
                "https://ark.cn-beijing.volces.com/api/v3");
    }

    private static ResolvedLlmModel model(String id, String provider, String modelName, String baseUrl) {
        LlmModelProperties.ModelConfig config = new LlmModelProperties.ModelConfig();
        config.setProvider(provider);
        config.setModelName(modelName);
        config.setBaseUrl(baseUrl);
        return ResolvedLlmModel.builder()
                .id(id)
                .displayName(id)
                .provider(provider)
                .config(config)
                .build();
    }
}
