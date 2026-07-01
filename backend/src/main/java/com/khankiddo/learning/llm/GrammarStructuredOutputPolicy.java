package com.khankiddo.learning.llm;

/**
 * Stage2 语法分析结构化输出策略：按模型选择 response_format / strict / max_tokens 行为。
 */
public interface GrammarStructuredOutputPolicy {

    boolean supports(ResolvedLlmModel model);

    GrammarStreamingModelSpec buildSpec(ResolvedLlmModel model);

    /**
     * 按厂商策略增强 Stage2 system prompt（千问 JSON Mode 需在 prompt 中描述 Schema）。
     */
    default String composeSystemPrompt(String basePrompt) {
        return basePrompt;
    }
}
