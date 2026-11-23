package com.khankiddo.learning.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPracticeAttemptRequest {

    @NotBlank(message = "userResponse 不能为空")
    private String userResponse;

    private String expectedResponse;

    @NotNull(message = "isCorrect 不能为空")
    private Boolean isCorrect;

    @NotNull(message = "independent 不能为空")
    private Boolean independent;

    @NotNull(message = "hintUsed 不能为空")
    private Boolean hintUsed;

    @NotNull(message = "score 不能为空")
    private BigDecimal score;

    private String feedback;

    private String evaluationJson;
}
