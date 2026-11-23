package com.khankiddo.learning.memory.assessment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryAssessmentResult {

    private List<EvaluationDto> evaluations;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvaluationDto {
        private String targetPointId;
        private String outcome;
        private Boolean isCorrect;
        private Boolean independent;
        private Boolean hintUsed;
        private Double score;
        private String evidenceQuote;
        private String expectedResponse;
        private String feedback;
    }
}
