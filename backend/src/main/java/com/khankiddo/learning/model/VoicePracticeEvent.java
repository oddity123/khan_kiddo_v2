package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语音练习本场学习证据：focus / attempt / note 共用一行形态，按 eventType 区分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoicePracticeEvent {

    public static final String TYPE_FOCUS = "focus";
    public static final String TYPE_ATTEMPT = "attempt";
    public static final String TYPE_NOTE = "note";

    private Long id;
    private Long practiceSessionId;
    private Long userId;
    private String eventType;
    private String cardId;
    private String analysisId;
    private String outcome;
    private String userUtterance;
    private String reason;
    /** note 正文；focus 时存 promptHint */
    private String noteText;
    private String statusBefore;
    private String statusAfter;
    private LocalDateTime createdAt;
}
