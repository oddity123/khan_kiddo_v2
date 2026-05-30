package com.khankiddo.learning.dto;

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
public class RecentSentenceView {

    private String originalSentence;
    private String suggestion;
    private List<String> problemTypeTags;
    private LocalDateTime createdAt;
}
