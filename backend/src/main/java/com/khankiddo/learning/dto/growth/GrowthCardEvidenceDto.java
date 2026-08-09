package com.khankiddo.learning.dto.growth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthCardEvidenceDto {

    private String sentenceId;
    private String originalSentence;
    private String suggestion;
    private int sortOrder;
}
