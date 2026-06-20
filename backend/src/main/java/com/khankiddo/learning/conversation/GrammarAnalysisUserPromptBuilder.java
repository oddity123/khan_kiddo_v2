package com.khankiddo.learning.conversation;

import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Stage2 用户 prompt：仅包含待分析的用户句子（分批 / 非分批统一格式）。
 */
@Component
@RequiredArgsConstructor
public class GrammarAnalysisUserPromptBuilder {

    private final PromptLoader promptLoader;

    public String buildFromUserSentences(List<String> userSentences) {
        if (CollectionUtils.isEmpty(userSentences)) {
            return promptLoader.fillTemplate(
                    promptLoader.getConversationAnalysisTemplate(),
                    "conversationContent",
                    "");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < userSentences.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(i + 1).append(". ").append(userSentences.get(i));
        }
        return promptLoader.fillTemplate(
                promptLoader.getConversationAnalysisTemplate(),
                "conversationContent",
                sb.toString());
    }
}
