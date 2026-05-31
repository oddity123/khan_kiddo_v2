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
public class GrammarAnalysisResult {

    @Builder.Default
    private List<GrammarSentenceItemDto> items = new ArrayList<>();
}
