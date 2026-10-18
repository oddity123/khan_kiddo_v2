package com.khankiddo.learning.dto.voice;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class VoicePracticeSessionResponse {

    long id;
    LocalDateTime startedAt;
    LocalDateTime endedAt;
    int wallDurationSeconds;
    boolean stoppedByCap;
    boolean transcriptSaved;
    String transcript;
}
