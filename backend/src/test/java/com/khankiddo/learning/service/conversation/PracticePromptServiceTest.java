package com.khankiddo.learning.service.conversation;

import com.khankiddo.learning.dto.conversation.PracticePromptRequest;
import com.khankiddo.learning.dto.conversation.PracticePromptResponse;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.prompt.PromptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticePromptServiceTest {

    private PracticePromptService service;

    @BeforeEach
    void setUp() {
        service = new PracticePromptService(new PromptLoader());
    }

    @Test
    void assemblesThreeGoalsInRankOrder() {
        PracticePromptRequest request = PracticePromptRequest.builder()
                .goals(List.of(
                        goal(3, "第三人称单数", "本场漏加 s", "先看主语", "He go.", "He goes."),
                        goal(1, "过去时", "用了现在时", "先定时间", "Yesterday I go.", "Yesterday I went."),
                        goal(2, "冠词", "首次提及漏 a", "先判断是否已知", "I saw cat.", "I saw a cat.")))
                .build();

        String prompt = service.assemble(request).getPrompt();

        assertThat(prompt).contains("你是我的英语口语陪练");
        assertThat(prompt.indexOf("1. 过去时")).isLessThan(prompt.indexOf("2. 冠词"));
        assertThat(prompt.indexOf("2. 冠词")).isLessThan(prompt.indexOf("3. 第三人称单数"));
        assertThat(prompt).contains("诊断：用了现在时");
        assertThat(prompt).contains("教练提示：先定时间");
        assertThat(prompt).contains("Yesterday I go. → Yesterday I went.");
        assertThat(prompt).doesNotContain("【附加词汇】");
        assertThat(prompt).doesNotContain("pointId");
        assertThat(prompt).doesNotContain("habitKey");
        assertThat(prompt).doesNotContain("null");
    }

    @Test
    void assemblesTwoAndOneGoal() {
        String two = service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal(2, "冠词"), goal(1, "过去时")))
                .build()).getPrompt();
        String twoGoals = two.substring(two.indexOf("【本场薄弱点】"), two.indexOf("训练规则"));
        assertThat(twoGoals).contains("1. 过去时").contains("2. 冠词").doesNotContain("3.");

        String one = service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal(1, "过去时")))
                .build()).getPrompt();
        String oneGoals = one.substring(one.indexOf("【本场薄弱点】"), one.indexOf("训练规则"));
        assertThat(oneGoals).contains("1. 过去时").doesNotContain("2.");
    }

    @Test
    void omitsOptionalGoalLinesWhenBlank() {
        PracticePromptRequest.Goal goal = PracticePromptRequest.Goal.builder()
                .rank(1)
                .title("过去时")
                .diagnosis("  ")
                .coaching(null)
                .originalSentence("Yesterday I go.")
                .targetSentence("  ")
                .build();

        String prompt = service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal))
                .build()).getPrompt();

        assertThat(prompt).contains("1. 过去时");
        assertThat(prompt).doesNotContain("诊断：");
        assertThat(prompt).doesNotContain("教练提示：");
        assertThat(prompt).doesNotContain("本场证据：");
        assertThat(prompt).doesNotContain("诊断：null");
    }

    @Test
    void vocabularyOnlyOmitsGoalBlock() {
        PracticePromptRequest request = PracticePromptRequest.builder()
                .vocabulary(List.of(vocab("售后服务", "after-sales service", "How can I say 售后服务?")))
                .build();

        String prompt = service.assemble(request).getPrompt();

        assertThat(prompt).contains("【附加词汇】");
        assertThat(prompt).contains("售后服务 → after-sales service");
        assertThat(prompt).contains("原句：How can I say 售后服务?");
        assertThat(prompt).doesNotContain("【本场薄弱点】");
        assertThat(prompt).contains("若只有附加词汇、没有薄弱点");
    }

    @Test
    void omitsVocabularyBlockWhenEmpty() {
        String prompt = service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal(1, "过去时")))
                .vocabulary(List.of())
                .build()).getPrompt();

        assertThat(prompt).contains("【本场薄弱点】");
        assertThat(prompt).doesNotContain("【附加词汇】");
    }

    @Test
    void trimsAndDeduplicatesVocabulary() {
        PracticePromptRequest request = PracticePromptRequest.builder()
                .goals(List.of(goal(1, " 过去时 ")))
                .vocabulary(List.of(
                        vocab(" 售后服务 ", " after-sales service ", "  How can I say 售后服务?  "),
                        vocab("售后服务", "after-sales service", "ignored duplicate"),
                        vocab("宣传", "promote", null)))
                .build();

        String prompt = service.assemble(request).getPrompt();

        assertThat(prompt).contains("1. 过去时");
        assertThat(prompt).contains("售后服务 → after-sales service");
        assertThat(prompt).contains("宣传 → promote");
        assertThat(prompt).contains("原句：How can I say 售后服务?");
        assertThat(prompt).doesNotContain("ignored duplicate");
        assertThat(countOccurrences(prompt, "售后服务 → after-sales service")).isEqualTo(1);
    }

    @Test
    void rejectsEmptyGoalsAndVocabulary() {
        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("请至少选择一项薄弱点或词汇");

        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .goals(List.of())
                .vocabulary(List.of())
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("请至少选择一项薄弱点或词汇");
    }

    @Test
    void rejectsMoreThanThreeItems() {
        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal(1, "a"), goal(2, "b"), goal(3, "c"),
                        PracticePromptRequest.Goal.builder().rank(1).title("d").build()))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("薄弱点最多 3 项");

        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .vocabulary(List.of(
                        vocab("a", "a", null),
                        vocab("b", "b", null),
                        vocab("c", "c", null),
                        vocab("d", "d", null)))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("词汇最多 3 项");
    }

    @Test
    void rejectsDuplicateOrOutOfRangeRank() {
        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal(1, "过去时"), goal(1, "冠词")))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("rank 不能重复");

        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .goals(List.of(PracticePromptRequest.Goal.builder().rank(4).title("越界").build()))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("rank 仅允许 1–3");
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .goals(List.of(PracticePromptRequest.Goal.builder().rank(1).title("  ").build()))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("薄弱点标题不能为空");

        assertThatThrownBy(() -> service.assemble(PracticePromptRequest.builder()
                .vocabulary(List.of(vocab("  ", "promote", null)))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("词汇正面不能为空");
    }

    @Test
    void responseContainsTrainingRules() {
        PracticePromptResponse response = service.assemble(PracticePromptRequest.builder()
                .goals(List.of(goal(1, "过去时")))
                .build());

        assertThat(response.getPrompt())
                .contains("每轮只回复 1–3 句")
                .contains("结束练习")
                .contains("8 个");
    }

    private static PracticePromptRequest.Goal goal(int rank, String title) {
        return PracticePromptRequest.Goal.builder().rank(rank).title(title).build();
    }

    private static PracticePromptRequest.Goal goal(
            int rank, String title, String diagnosis, String coaching, String original, String target) {
        return PracticePromptRequest.Goal.builder()
                .rank(rank)
                .title(title)
                .diagnosis(diagnosis)
                .coaching(coaching)
                .originalSentence(original)
                .targetSentence(target)
                .build();
    }

    private static PracticePromptRequest.Vocabulary vocab(String front, String back, String original) {
        return PracticePromptRequest.Vocabulary.builder()
                .front(front)
                .back(back)
                .originalSentence(original)
                .build();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
    }
}
