package com.khankiddo.learning.llm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按 {@link GrammarStructuredOutputPolicy} 为 Stage2 组装 system prompt。
 */
@Component
public class GrammarSystemPromptComposer {

    private final List<GrammarStructuredOutputPolicy> grammarStructuredOutputPolicies;

    public GrammarSystemPromptComposer(List<GrammarStructuredOutputPolicy> grammarStructuredOutputPolicies) {
        this.grammarStructuredOutputPolicies = grammarStructuredOutputPolicies;
    }

    public String compose(String basePrompt, ResolvedLlmModel model) {
        return grammarStructuredOutputPolicies.stream()
                .filter(policy -> policy.supports(model))
                .findFirst()
                .map(policy -> policy.composeSystemPrompt(basePrompt))
                .orElse(basePrompt);
    }
}
