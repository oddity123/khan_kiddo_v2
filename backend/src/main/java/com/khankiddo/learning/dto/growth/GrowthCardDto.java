package com.khankiddo.learning.dto.growth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthCardDto {

    private String cardId;
    private String type;
    private String status;
    private LocalDate nextDueAt;
    private String front;
    private String back;
    private String sourceAnalysisId;
}
