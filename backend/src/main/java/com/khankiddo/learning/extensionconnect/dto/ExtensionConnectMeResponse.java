package com.khankiddo.learning.extensionconnect.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ExtensionConnectMeResponse {
    boolean connected;
    String username;
    Long credentialId;
    LocalDateTime expiresAt;
}
