package com.khankiddo.learning.service.conversation;

import com.khankiddo.learning.dto.conversation.PracticePromptRequest;
import com.khankiddo.learning.dto.conversation.PracticePromptResponse;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PracticePromptService {

    private static final int MAX_ITEMS = 3;

    private final PromptLoader promptLoader;

    public PracticePromptResponse assemble(PracticePromptRequest request) {
        if (ObjectUtils.isEmpty(request)) {
            throw new BadRequestException("请至少选择一项薄弱点或词汇");
        }
        List<PracticePromptRequest.Goal> goals = sanitizeGoals(request.getGoals());
        List<PracticePromptRequest.Vocabulary> vocabulary = sanitizeVocabulary(request.getVocabulary());
        if (CollectionUtils.isEmpty(goals) && CollectionUtils.isEmpty(vocabulary)) {
            throw new BadRequestException("请至少选择一项薄弱点或词汇");
        }

        String template = promptLoader.getPracticePromptTemplate();
        String prompt = promptLoader.fillTemplate(template, "goals", formatGoals(goals));
        prompt = promptLoader.fillTemplate(prompt, "vocabulary", formatVocabulary(vocabulary));
        prompt = collapseBlankLines(prompt);

        return PracticePromptResponse.builder().prompt(prompt).build();
    }

    private List<PracticePromptRequest.Goal> sanitizeGoals(List<PracticePromptRequest.Goal> raw) {
        if (CollectionUtils.isEmpty(raw)) {
            return List.of();
        }
        if (raw.size() > MAX_ITEMS) {
            throw new BadRequestException("薄弱点最多 3 项");
        }
        List<PracticePromptRequest.Goal> cleaned = new ArrayList<>();
        Set<Integer> ranks = new HashSet<>();
        for (PracticePromptRequest.Goal item : raw) {
            if (ObjectUtils.isEmpty(item)) {
                continue;
            }
            Integer rank = item.getRank();
            if (ObjectUtils.isEmpty(rank) || rank < 1 || rank > MAX_ITEMS) {
                throw new BadRequestException("rank 仅允许 1–3");
            }
            if (!ranks.add(rank)) {
                throw new BadRequestException("rank 不能重复");
            }
            String title = trimToNull(item.getTitle());
            if (!StringUtils.hasText(title)) {
                throw new BadRequestException("薄弱点标题不能为空");
            }
            cleaned.add(PracticePromptRequest.Goal.builder()
                    .rank(rank)
                    .title(title)
                    .diagnosis(trimToNull(item.getDiagnosis()))
                    .coaching(trimToNull(item.getCoaching()))
                    .originalSentence(trimToNull(item.getOriginalSentence()))
                    .targetSentence(trimToNull(item.getTargetSentence()))
                    .build());
        }
        cleaned.sort(Comparator.comparingInt(PracticePromptRequest.Goal::getRank));
        return cleaned;
    }

    private List<PracticePromptRequest.Vocabulary> sanitizeVocabulary(
            List<PracticePromptRequest.Vocabulary> raw) {
        if (CollectionUtils.isEmpty(raw)) {
            return List.of();
        }
        if (raw.size() > MAX_ITEMS) {
            throw new BadRequestException("词汇最多 3 项");
        }
        Map<String, PracticePromptRequest.Vocabulary> unique = new LinkedHashMap<>();
        for (PracticePromptRequest.Vocabulary item : raw) {
            if (ObjectUtils.isEmpty(item)) {
                continue;
            }
            String front = trimToNull(item.getFront());
            String back = trimToNull(item.getBack());
            if (!StringUtils.hasText(front)) {
                throw new BadRequestException("词汇正面不能为空");
            }
            if (!StringUtils.hasText(back)) {
                throw new BadRequestException("词汇背面不能为空");
            }
            String key = front + "\n" + back;
            unique.putIfAbsent(key, PracticePromptRequest.Vocabulary.builder()
                    .front(front)
                    .back(back)
                    .originalSentence(trimToNull(item.getOriginalSentence()))
                    .build());
        }
        return new ArrayList<>(unique.values());
    }

    private String formatGoals(List<PracticePromptRequest.Goal> goals) {
        if (CollectionUtils.isEmpty(goals)) {
            return "";
        }
        StringBuilder block = new StringBuilder();
        block.append("## Weak points\n");
        block.append("Go in numbered order. Finish one before the next.");
        for (PracticePromptRequest.Goal goal : goals) {
            block.append("\n\n### ").append(goal.getRank()).append(". ").append(goal.getTitle());
            appendMarkdownItem(block, "Last time", goal.getDiagnosis());
            appendMarkdownItem(block, "Cue", goal.getCoaching());
            if (StringUtils.hasText(goal.getOriginalSentence())
                    && StringUtils.hasText(goal.getTargetSentence())) {
                block.append("\n- Evidence: `")
                        .append(goal.getOriginalSentence())
                        .append("` → `")
                        .append(goal.getTargetSentence())
                        .append('`');
            }
        }
        return block.toString();
    }

    private String formatVocabulary(List<PracticePromptRequest.Vocabulary> vocabulary) {
        if (CollectionUtils.isEmpty(vocabulary)) {
            return "";
        }
        StringBuilder block = new StringBuilder();
        block.append("## Extra vocabulary\n");
        block.append("Add-ons only. Recap after weak points.");
        for (PracticePromptRequest.Vocabulary item : vocabulary) {
            block.append("\n- **")
                    .append(item.getFront())
                    .append("** → ")
                    .append(item.getBack());
            if (StringUtils.hasText(item.getOriginalSentence())) {
                block.append("\n  - Last said: `")
                        .append(item.getOriginalSentence())
                        .append('`');
            }
        }
        return block.toString();
    }

    private static void appendMarkdownItem(StringBuilder block, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        block.append("\n- ").append(label).append(": ").append(value);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String collapseBlankLines(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "";
        }
        return prompt.replaceAll("\\n{3,}", "\n\n").trim() + "\n";
    }
}
