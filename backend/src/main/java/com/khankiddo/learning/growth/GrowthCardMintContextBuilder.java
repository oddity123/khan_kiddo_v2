package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.PracticePromptDto;
import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 组装「本场说话习惯 → 生成一张 habit 知识卡」的用户消息。
 * 模板见 {@code templates/prompts/growth-card-mint-prompt-template.txt}。
 */
@Component
@RequiredArgsConstructor
public class GrowthCardMintContextBuilder {

    private final PromptLoader promptLoader;

    public String build(ActionCardDto habit) {
        String rankLabel = habit.getRank() > 0 ? "Top " + habit.getRank() : "本场";
        String evidence = buildEvidence(habit);
        String practiceTarget = resolvePracticeTarget(habit);

        String prompt = promptLoader.getGrowthCardMintTemplate();
        prompt = promptLoader.fillTemplate(prompt, "rankLabel", nullToEmpty(rankLabel));
        prompt = promptLoader.fillTemplate(prompt, "headline", nullToEmpty(habit.getHeadlineZh()));
        prompt = promptLoader.fillTemplate(prompt, "title", nullToEmpty(habit.getTitleZh()));
        prompt = promptLoader.fillTemplate(prompt, "why", nullToEmpty(habit.getWhyZh()));
        prompt = promptLoader.fillTemplate(prompt, "evidence", evidence);
        prompt = promptLoader.fillTemplate(prompt, "practiceTarget", practiceTarget);
        return prompt;
    }

    private static String buildEvidence(ActionCardDto habit) {
        if (CollectionUtils.isEmpty(habit.getExamples())) {
            return "";
        }
        StringBuilder evidence = new StringBuilder();
        for (ActionCardDto.ExampleDto example : habit.getExamples()) {
            evidence.append("- original: ").append(nullToEmpty(example.getOriginalSentence()))
                    .append(" | suggestion: ").append(nullToEmpty(example.getSuggestion()))
                    .append('\n');
        }
        return evidence.toString().trim();
    }

    private static String resolvePracticeTarget(ActionCardDto habit) {
        PracticePromptDto practicePrompt = habit.getPracticePrompt();
        if (practicePrompt != null && StringUtils.hasText(practicePrompt.getTargetSentence())) {
            return practicePrompt.getTargetSentence().trim();
        }
        return nullToEmpty(habit.getActionHintZh());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
