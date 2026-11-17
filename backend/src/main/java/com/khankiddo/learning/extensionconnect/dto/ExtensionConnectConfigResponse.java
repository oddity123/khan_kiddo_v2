package com.khankiddo.learning.extensionconnect.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtensionConnectConfigResponse {
    boolean enabled;
    int sessionTtlMinutes;
    int credentialTtlDays;
    long pollIntervalMs;
    long pollTimeoutMs;
}
