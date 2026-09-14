package com.khankiddo.learning.growth;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * LLM 切 focusPhrase 出口（后续实现）。当前不发起任何模型调用，恒返回 empty。
 */
@Component
public class LlmFocusPhraseCutter implements FocusPhraseCutStrategy {

    @Override
    public Optional<FocusPhrasePair> cut(FocusPhraseCutRequest request) {
        return Optional.empty();
    }
}
