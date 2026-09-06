package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderSupportTest {

    @Test
    void deepseekOnDashScope_doesNotSupportJsonSchema() {
        ResolvedLlmModel model = model("deepseek-v4-flash-0731", "qwen",
                "deepseek-v4-flash-0731",
                "https://dashscope.aliyuncs.com/compatible-mode/v1");

        assertThat(LlmProviderSupport.isDashScopeCompatible(model)).isTrue();
        assertThat(LlmProviderSupport.isDeepSeek(model)).isTrue();
        assertThat(LlmProviderSupport.supportsJsonSchemaResponseFormat(model)).isFalse();
    }

    @Test
    void doubao_supportsJsonSchema() {
        ResolvedLlmModel model = model("doubao-seed", "doubao",
                "doubao-seed-1-8-251228",
                "https://ark.cn-beijing.volces.com/api/v3");

        assertThat(LlmProviderSupport.supportsJsonSchemaResponseFormat(model)).isTrue();
    }

    @Test
    void qwen37Plus_supportsJsonSchema() {
        ResolvedLlmModel model = model("qwen3.7-plus", "qwen",
                "qwen3.7-plus",
                "https://dashscope.aliyuncs.com/compatible-mode/v1");

        assertThat(LlmProviderSupport.supportsJsonSchemaResponseFormat(model)).isTrue();
    }

    private static ResolvedLlmModel model(String id, String provider, String modelName, String baseUrl) {
        LlmModelProperties.ModelConfig config = new LlmModelProperties.ModelConfig();
        config.setId(id);
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
