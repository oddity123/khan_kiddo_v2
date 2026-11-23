package com.khankiddo.learning.dto.dailypractice;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPracticeMarkPracticedRequest {

    @NotEmpty(message = "memoryIds 不能为空")
    private List<Long> memoryIds;

    /** 可选外部会话标识（若在已有 ChatGPT 对话中注入可直接绑定）。 */
    private String sourceConversationId;

    /** 练习形态（默认 SCENARIO）。 */
    private String practiceType;
}
