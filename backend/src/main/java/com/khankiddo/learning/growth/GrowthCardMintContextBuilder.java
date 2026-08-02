package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.dto.conversation.PracticePromptDto;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class GrowthCardMintContextBuilder {

    public String build(String analysisId, ActionCardDto topHabit) {
        String habitKey = StringUtils.hasText(topHabit.getHabitKey())
                ? topHabit.getHabitKey()
                : topHabit.getPointId();
        StringBuilder brief = new StringBuilder()
                .append("analysisId: ").append(analysisId).append('\n')
                .append("sourceRef: habit:").append(habitKey).append('\n')
                .append("headline: ").append(topHabit.getHeadlineZh()).append('\n')
                .append("why: ").append(topHabit.getWhyZh()).append('\n')
                .append("evidence:\n");

        if (!CollectionUtils.isEmpty(topHabit.getExamples())) {
            for (ActionCardDto.ExampleDto example : topHabit.getExamples()) {
                brief.append("- original: ").append(example.getOriginalSentence())
                        .append(" | suggestion: ").append(example.getSuggestion()).append('\n');
            }
        }

        PracticePromptDto practicePrompt = topHabit.getPracticePrompt();
        if (practicePrompt != null && StringUtils.hasText(practicePrompt.getTargetSentence())) {
            brief.append("practiceTarget: ").append(practicePrompt.getTargetSentence()).append('\n');
        } else {
            brief.append("practiceTarget: ").append(topHabit.getActionHintZh()).append('\n');
        }
        return brief.append("请生成一张 habit 成长卡并 persist。").toString();
    }
}
