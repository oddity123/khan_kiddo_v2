package com.khankiddo.learning.dto.voice;

/** 教练在本场追加的备注（不是独立笔记本实体）。 */
public record VoiceLearningNoteDto(
        long noteId,
        String text,
        String cardId,
        String cardFront,
        String analysisId,
        String createdAt) {
}
