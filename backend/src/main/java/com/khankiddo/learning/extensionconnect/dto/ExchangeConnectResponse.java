package com.khankiddo.learning.extensionconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExchangeConnectResponse {
    String status;
    /** 仅 status=EXCHANGED 时返回一次。 */
    String token;
    String username;
    LocalDateTime expiresAt;
}
