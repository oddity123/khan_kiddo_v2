package com.khankiddo.learning.memory.dto;

import jakarta.validation.constraints.NotBlank;
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
public class BindPracticeConversationRequest {

    @NotEmpty(message = "sessionIds 不能为空")
    private List<Long> sessionIds;

    @NotBlank(message = "sourceConversationId 不能为空")
    private String sourceConversationId;
}
