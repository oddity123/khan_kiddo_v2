package com.khankiddo.learning.llm;

import com.khankiddo.learning.util.SchemaLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 千问 / DashScope：不走 API JSON Mode，把 Schema 写入 system prompt；省略 max_tokens，避免输出被截断。
 */
@Component
@Order(100)
public class QwenGrammarStructuredOutputPolicy implements GrammarStructuredOutputPolicy {

    private static final String QWEN_PROMPT_SCHEMA_SUFFIX = "|qwen-prompt-schema";

    private final SchemaLoader schemaLoader;

    public QwenGrammarStructuredOutputPolicy(SchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    @Override
    public boolean supports(ResolvedLlmModel model) {
        return LlmProviderSupport.isDashScopeCompatible(model);
    }

    @Override
    public GrammarStreamingModelSpec buildSpec(ResolvedLlmModel model) {
        return GrammarStreamingModelSpec.builder()
                .cacheSuffix(QWEN_PROMPT_SCHEMA_SUFFIX)
                .responseFormat(null)
                .strictJsonSchema(false)
                .omitMaxTokens(true)
                .build();
    }

    @Override
    public String composeSystemPrompt(String basePrompt) {
        String schema = schemaLoader.getConversationAnalysisSchema();
        return basePrompt + """

                ## JSON Schema（输出必须严格遵循）
                请仅输出符合下列 JSON Schema 的 JSON 对象（由 prompt 约束，不设置 API response_format）：
                - 不要 markdown 代码围栏、不要额外说明文字
                - 所有 string 字段须合法 JSON 转义
                - 仅包含有问题的句子；无问题句子不要出现在 items 中

                """ + schema;
    }
}
