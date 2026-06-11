package com.khankiddo.learning.dto.langchain4j;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Langchain4jLearningChatRequest(
        @NotBlank(message = "请输入问题")
        @Size(max = 2000, message = "问题不能超过 2000 个字符")
        String message) {
}
