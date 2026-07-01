package com.khankiddo.learning.dto.grammar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GrammarErrorChatRequest {

    @NotBlank(message = "请输入问题")
    @Size(max = 1000, message = "问题过长")
    private String message;
}
