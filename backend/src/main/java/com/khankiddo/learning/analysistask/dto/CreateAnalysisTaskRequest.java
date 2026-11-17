package com.khankiddo.learning.analysistask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAnalysisTaskRequest {

    @NotBlank(message = "对话内容不能为空")
    private String conversationContent;

    @NotBlank(message = "来源类型不能为空")
    @Size(max = 32)
    private String sourceType;

    @Size(max = 64)
    private String sourceConversationId;

    @Size(max = 100)
    private String modelId;
}
