package com.khankiddo.learning.voice;

import com.khankiddo.learning.dto.voice.VoiceLearningAttemptDto;
import com.khankiddo.learning.dto.voice.VoiceLearningFocusDto;
import com.khankiddo.learning.dto.voice.VoiceLearningNoteDto;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;

import java.time.LocalDateTime;

/**
 * 证据行 → 面板 DTO 的单点映射：工具事件与 REST hydrate 共用，保证两边形状一致。
 */
public final class VoiceLearningDtoSupport {

    private VoiceLearningDtoSupport() {
    }

    public static VoiceLearningFocusDto focus(VoicePracticeEvent row, GrowthCard card) {
        return new VoiceLearningFocusDto(
                idOf(row),
                row.getCardId(),
                card == null ? "" : nullToEmpty(card.getType()),
                card == null ? "" : nullToEmpty(card.getFront()),
                card == null ? "" : nullToEmpty(card.getBack()),
                card == null ? "" : nullToEmpty(card.getStatus()),
                row.getNoteText(),
                iso(row.getCreatedAt()));
    }

    public static VoiceLearningAttemptDto attempt(VoicePracticeEvent row, GrowthCard card) {
        return new VoiceLearningAttemptDto(
                idOf(row),
                row.getCardId(),
                card == null ? "" : nullToEmpty(card.getFront()),
                row.getOutcome(),
                row.getUserUtterance(),
                row.getReason(),
                row.getStatusBefore(),
                row.getStatusAfter(),
                iso(row.getCreatedAt()));
    }

    public static VoiceLearningNoteDto note(VoicePracticeEvent row, GrowthCard card) {
        return new VoiceLearningNoteDto(
                idOf(row),
                nullToEmpty(row.getNoteText()),
                row.getCardId(),
                card == null ? null : nullToEmpty(card.getFront()),
                row.getAnalysisId(),
                iso(row.getCreatedAt()));
    }

    private static long idOf(VoicePracticeEvent row) {
        return row.getId() == null ? 0L : row.getId();
    }

    private static String iso(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
