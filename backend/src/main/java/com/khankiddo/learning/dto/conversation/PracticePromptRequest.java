package com.khankiddo.learning.dto.conversation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 定向复练提示词请求：前端提交已选 Top 项与词汇，后端套模板组装。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticePromptRequest {

    @Valid
    @Size(max = 3, message = "薄弱点最多 3 项")
    private List<Goal> goals;

    @Valid
    @Size(max = 3, message = "词汇最多 3 项")
    private List<Vocabulary> vocabulary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Goal {

        @NotNull(message = "rank 不能为空")
        @Min(value = 1, message = "rank 仅允许 1–3")
        @Max(value = 3, message = "rank 仅允许 1–3")
        private Integer rank;

        @NotBlank(message = "薄弱点标题不能为空")
        @Size(max = 200, message = "薄弱点标题不能超过 200 个字符")
        private String title;

        @Size(max = 800, message = "诊断不能超过 800 个字符")
        private String diagnosis;

        @Size(max = 800, message = "教练提示不能超过 800 个字符")
        private String coaching;

        @Size(max = 800, message = "原句不能超过 800 个字符")
        private String originalSentence;

        @Size(max = 800, message = "推荐表达不能超过 800 个字符")
        private String targetSentence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vocabulary {

        @NotBlank(message = "词汇正面不能为空")
        @Size(max = 200, message = "词汇正面不能超过 200 个字符")
        private String front;

        @NotBlank(message = "词汇背面不能为空")
        @Size(max = 400, message = "词汇背面不能超过 400 个字符")
        private String back;

        @Size(max = 800, message = "原句不能超过 800 个字符")
        private String originalSentence;
    }
}
