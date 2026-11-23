package com.khankiddo.learning.memory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemoryStatusRequest {

    @NotBlank(message = "status 不能为空")
    private String status;
}
