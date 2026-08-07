package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import com.khankiddo.learning.util.SchemaLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QwenGrammarStructuredOutputPolicyTest {

    @Test
    void buildSpecOmitsApiJsonModeAndMaxTokens() {
        QwenGrammarStructuredOutputPolicy policy = new QwenGrammarStructuredOutputPolicy(new SchemaLoader());
        ResolvedLlmModel model = qwenModel();

        assertThat(policy.supports(model)).isTrue();
        GrammarStreamingModelSpec spec = policy.buildSpec(model);
        assertThat(spec.getResponseFormat()).isNull();
        assertThat(spec.isStrictJsonSchema()).isFalse();
        assertThat(spec.isOmitMaxTokens()).isTrue();
        assertThat(spec.getCacheSuffix()).contains("qwen-prompt-schema");
    }

    @Test
    void composeSystemPromptAppendsConversationAnalysisSchema() {
        QwenGrammarStructuredOutputPolicy policy = new QwenGrammarStructuredOutputPolicy(new SchemaLoader());
        String composed = policy.composeSystemPrompt("BASE");

        assertThat(composed).startsWith("BASE");
        assertThat(composed).contains("JSON Schema");
        assertThat(composed).doesNotContain("response_format=json_object");
        assertThat(composed).contains("不设置 API response_format");
        assertThat(composed).contains("\"pointId\"");
        assertThat(composed).contains("STRUCTURE_OTHER");
    }

    private static ResolvedLlmModel qwenModel() {
        LlmModelProperties.ModelConfig config = new LlmModelProperties.ModelConfig();
        config.setProvider("qwen");
        config.setModelName("qwen-plus");
        config.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        config.setApiKeyEnv("QWEN_API_KEY");
        return ResolvedLlmModel.builder()
                .id("qwen-plus")
                .displayName("Qwen Plus")
                .provider("qwen")
                .config(config)
                .build();
    }
}
