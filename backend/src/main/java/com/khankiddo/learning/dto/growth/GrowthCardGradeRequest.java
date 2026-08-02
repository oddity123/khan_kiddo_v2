package com.khankiddo.learning.dto.growth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GrowthCardGradeRequest {

    @NotBlank
    private String grade;
}
