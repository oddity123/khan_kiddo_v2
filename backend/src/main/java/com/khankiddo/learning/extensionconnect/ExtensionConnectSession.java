package com.khankiddo.learning.extensionconnect;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionConnectSession {

    private String id;
    private String codeChallenge;
    private String status;
    private Long userId;
    private LocalDateTime expiresAt;
    private LocalDateTime authorizedAt;
    private LocalDateTime exchangedAt;
    private LocalDateTime createdAt;
}
