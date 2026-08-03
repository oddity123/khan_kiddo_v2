package com.khankiddo.learning.dto.growth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MintHabitGrowthCardRequest {

    /** 行动卡 habitKey（或 pointId），对应 sourceRef = habit:{habitKey} */
    @NotBlank
    private String habitKey;
}
