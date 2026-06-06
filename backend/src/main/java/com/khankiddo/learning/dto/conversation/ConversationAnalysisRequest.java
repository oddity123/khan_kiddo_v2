package com.khankiddo.learning.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversationAnalysisRequest {

    @NotBlank(message = "对话内容不能为空")
    private String conversationContent;

    /**
     * 分析模型配置 ID（Stage2/Stage3），空则使用 app.llm.default-model-id
     */
    private String modelId;
}
