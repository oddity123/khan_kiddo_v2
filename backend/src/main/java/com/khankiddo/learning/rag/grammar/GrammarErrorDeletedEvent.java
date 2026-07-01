package com.khankiddo.learning.rag.grammar;

import java.util.List;

public record GrammarErrorDeletedEvent(
        Long userId,
        String analysisId,
        List<Long> sentenceIds) {
}
