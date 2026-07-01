package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.model.ConversationAnalysisItem;

import java.util.List;

public record GrammarErrorIndexedEvent(
        Long userId,
        String analysisId,
        List<ConversationAnalysisItem> items) {
}
