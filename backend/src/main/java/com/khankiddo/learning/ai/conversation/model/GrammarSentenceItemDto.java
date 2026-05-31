package com.khankiddo.learning.ai.conversation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarSentenceItemDto {

    private String originalSentence;
    private String suggestion;

    @Builder.Default
    private List<GrammarErrorDto> errors = new ArrayList<>();
}
