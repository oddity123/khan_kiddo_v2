package com.khankiddo.learning.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversationAnalysisRequest {

    @NotBlank(message = "对话内容不能为空")
    private String conversationContent;
}
