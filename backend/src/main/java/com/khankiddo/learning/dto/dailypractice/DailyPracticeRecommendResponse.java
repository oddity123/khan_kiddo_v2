package com.khankiddo.learning.dto.dailypractice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPracticeRecommendResponse {

    /** 无可用薄弱点记忆且无到期表达时为 true（仍返回通用开场 prompt）。 */
    private boolean empty;

    /** 扩展弹窗紧凑摘要。 */
    private String summary;

    /** 可注入 ChatGPT 输入框的完整教练指令。 */
    private String prompt;

    @Builder.Default
    private List<GoalSummary> goals = new ArrayList<>();

    @Builder.Default
    private List<VocabSummary> vocabulary = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalSummary {
        /** 冷启动即时回退时可能为空（尚未沉淀记忆行）。 */
        private Long memoryId;
        private String title;
        private String pointId;
        private String familyId;
        private String memoryType;
        private String status;
        private BigDecimal masteryLevel;
        private Double priorityScore;
        /** 读时取自最新命中证据，不冗余落记忆主表。 */
        private String evidenceOriginal;
        private String evidenceRewrite;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VocabSummary {
        private String cardId;
        private String type;
        private String front;
        private String back;
    }
}
