package com.khankiddo.learning.dto.voice;

import java.util.List;

/** {@code GET /api/voice/practice/sessions/{id}/learning}：刷新后恢复右侧面板。 */
public record VoiceLearningPanelResponse(
        VoiceLearningFocusDto focus,
        List<VoiceLearningAttemptDto> attempts,
        List<VoiceLearningNoteDto> notes) {
}
