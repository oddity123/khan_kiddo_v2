package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.model.UserWeaknessMemory;

import java.util.List;

/**
 * Java 预查装配结果，供单次结构化 LLM 归并使用。
 */
public record WeaknessMemoryPrefetchContext(
        Long userId,
        String analysisId,
        List<UserWeaknessMemory> activeMemories,
        List<ActionCardDto> actionCards,
        List<CardEvidence> cardEvidences
) {
    public record CardEvidence(
            String habitKey,
            String pointId,
            String statsSummary,
            List<ExampleLine> historicalExamples,
            List<String> similarSnippets
    ) {
    }

    public record ExampleLine(
            String analysisId,
            String originalSentence,
            String suggestion
    ) {
    }
}
