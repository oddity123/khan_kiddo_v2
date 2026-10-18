package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelEvent {

    private Long id;
    private Long userId;
    private String eventName;
    private Boolean guest;
    private Integer durationSeconds;
    private Long voiceSessionId;
}
