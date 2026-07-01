package com.khankiddo.learning.dto.grammar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarErrorSearchResponse {

    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String analysisId;
        private Long sentenceId;
        private String originalSentence;
        private List<String> problemTypes;
        private List<String> errorPoints;
        private String suggestion;
        private Double score;
        private LocalDateTime createdAt;
    }
}
