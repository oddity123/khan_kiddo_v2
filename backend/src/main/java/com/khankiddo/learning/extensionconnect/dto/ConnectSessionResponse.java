package com.khankiddo.learning.extensionconnect.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ConnectSessionResponse {
    String sessionId;
    String status;
    LocalDateTime expiresAt;
}
