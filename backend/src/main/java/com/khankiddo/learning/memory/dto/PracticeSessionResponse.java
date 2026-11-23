package com.khankiddo.learning.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeSessionResponse {

    private Long id;
    private Long memoryId;
    private String practiceType;
    private String targetPointId;
    private String status;
    private String sourceConversationId;
    private String analysisId;
    private Integer totalAttempts;
    private Integer correctAttempts;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<PracticeAttemptResponse> attempts = new ArrayList<>();
}
