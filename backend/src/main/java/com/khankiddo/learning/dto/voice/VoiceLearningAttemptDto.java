package com.khankiddo.learning.dto.voice;

/** 一次回合尝试证据；statusAfter 仅在本次上调了进度时非空。 */
public record VoiceLearningAttemptDto(
        long attemptId,
        String cardId,
        String cardFront,
        String outcome,
        String userUtterance,
        String reason,
        String statusBefore,
        String statusAfter,
        String createdAt) {
}
