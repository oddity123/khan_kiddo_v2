package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoicePracticeSession {

    private Long id;
    private Long userId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer wallDurationSeconds;
    private Integer reservedSeconds;
    private Boolean stoppedByCap;
    private String transcript;
    private Boolean transcriptSaved;
}
