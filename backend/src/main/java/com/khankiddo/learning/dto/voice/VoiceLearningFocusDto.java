package com.khankiddo.learning.dto.voice;

/** 右侧面板「当前焦点卡」；REST hydrate 与 proxy.learning.focus 共用。 */
public record VoiceLearningFocusDto(
        long eventId,
        String cardId,
        String type,
        String front,
        String back,
        String status,
        String promptHint,
        String createdAt) {
}
