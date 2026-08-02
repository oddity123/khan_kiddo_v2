package com.khankiddo.learning.dto.conversation;

import com.khankiddo.learning.knowledge.CardKind;
import com.khankiddo.learning.knowledge.CardPolicy;
import com.khankiddo.learning.knowledge.PointChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    /** 排序分组键：family→familyId，leaf→pointId，channel→channel 值 */
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

    /** 仅 rule 家族填充：同家族其它叶子 pointId → count */
    private Map<String, Integer> siblingPoints;

    private String actionHintZh;

    private PracticePromptDto practicePrompt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExampleDto {
        private String originalSentence;
        private String errorPoint;
        private String suggestion;
    }
}
