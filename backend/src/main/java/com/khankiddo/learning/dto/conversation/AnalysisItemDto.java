package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisItemDto {

    private Long sentenceId;
    private String originalSentence;
    private String suggestion;
    private List<AnalysisErrorDto> errors;
}
