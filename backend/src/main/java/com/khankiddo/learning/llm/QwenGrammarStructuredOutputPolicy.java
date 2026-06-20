package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import com.khankiddo.learning.util.SchemaLoader;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 千问模型：使用 JSON Mode（json_object）并省略 max_tokens，避免输出被截断。
 */
@Component
@Order(100)
public class QwenGrammarStructuredOutputPolicy implements GrammarStructuredOutputPolicy {

    private static final String QWEN_PROVIDER = "qwen";
    private static final String QWEN_JSON_OBJECT_SUFFIX = "|qwen-json-object";
    private static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";

    private final SchemaLoader schemaLoader;

    public QwenGrammarStructuredOutputPolicy(SchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    @Override
    public boolean supports(ResolvedLlmModel model) {
        if (model == null) {
            return false;
        }
        if (StringUtils.hasText(model.getProvider())
                && QWEN_PROVIDER.equalsIgnoreCase(model.getProvider().trim())) {
            return true;
        }
        return isQwenModelConfig(model.getConfig());
    }

    @Override
    public GrammarStreamingModelSpec buildSpec(ResolvedLlmModel model) {
        return GrammarStreamingModelSpec.builder()
                .cacheSuffix(QWEN_JSON_OBJECT_SUFFIX)
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .strictJsonSchema(false)
                .omitMaxTokens(true)
                .build();
    }

    @Override
    public String composeSystemPrompt(String basePrompt) {
        String schema = schemaLoader.getConversationAnalysisSchema();
        return basePrompt + """

                ## JSON Schema（输出必须严格遵循）
                千问 JSON Mode 需在 prompt 中明确 Schema。请仅输出符合下列 JSON Schema 的 JSON 对象：
                - 不要 markdown 代码围栏、不要额外说明文字
                - 所有 string 字段须合法 JSON 转义
                - 仅包含有问题的句子；无问题句子不要出现在 items 中

                """ + schema;
    }

    private boolean isQwenModelConfig(LlmModelProperties.ModelConfig config) {
        if (config == null) {
            return false;
        }
        if (StringUtils.hasText(config.getProvider())
                && QWEN_PROVIDER.equalsIgnoreCase(config.getProvider().trim())) {
            return true;
        }
        if (StringUtils.hasText(config.getBaseUrl())
                && config.getBaseUrl().toLowerCase().contains(DASHSCOPE_HOST)) {
            return true;
        }
        return StringUtils.hasText(config.getModelName())
                && config.getModelName().toLowerCase().startsWith(QWEN_PROVIDER);
    }
}
