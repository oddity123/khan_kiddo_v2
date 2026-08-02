package com.khankiddo.learning.dto.growth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CollectGrowthCardRequest {

    @NotBlank
    private String analysisId;

    @NotBlank
    private String front;

    @NotBlank
    private String back;

    private String type;

    @NotBlank
    private String sourceRef;
}
