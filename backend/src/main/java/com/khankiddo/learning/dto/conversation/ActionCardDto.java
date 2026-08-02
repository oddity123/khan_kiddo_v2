package com.khankiddo.learning.dto.conversation;

import com.khankiddo.learning.knowledge.CardKind;
import com.khankiddo.learning.knowledge.CardPolicy;
import com.khankiddo.learning.knowledge.PointChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 跨通道习惯行动卡（Top 1-3），详情页 API 扩展见
 * docs/superpowers/specs/2026-08-01-conversation-analysis-action-cards-design.md §7.2。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionCardDto {

    /** 1-based 排名，1 即 topHabit */
    private int rank;

    private PointChannel channel;
    private CardKind cardKind;
    private CardPolicy cardPolicy;

    /**
     * 排序分组键：family→familyId，leaf→pointId，channel→{@link PointChannel#name()}
     * （如 {@code CHINESE}/{@code LEXICAL}，与 JSON 小写 channel 字段不同）。
     */
    private String habitKey;

    /** 组内代表叶子（出现最多/分数贡献最大） */
    private String pointId;

    /** rank=1 为完整结论句，rank=2/3 为 topTitleZh 本身 */
    private String headlineZh;

    private String titleZh;
    private String whyZh;

    /** 本场计入该习惯的证据条数 */
    private int errorCount;

    private double score;

    /** ≤5 条证据 */
    private List<ExampleDto> examples;

    /** 仅 rule 家族填充：同家族其它叶子（不含代表叶子） */
    private List<SiblingPointDto> siblingPoints;

    private String actionHintZh;

    private PracticePromptDto practicePrompt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExampleDto {
        private String sentenceId;
        private String originalSentence;
        private String errorPoint;
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiblingPointDto {
        private String pointId;
        private String titleZh;
        private int errorCount;
    }
}
