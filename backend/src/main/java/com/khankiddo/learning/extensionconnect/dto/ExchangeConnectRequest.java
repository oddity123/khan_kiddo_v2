package com.khankiddo.learning.extensionconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExchangeConnectRequest {

    @NotBlank(message = "sessionId 不能为空")
    @Size(max = 36)
    private String sessionId;

    @NotBlank(message = "codeVerifier 不能为空")
    @Size(min = 43, max = 128, message = "codeVerifier 长度无效")
    private String codeVerifier;
}
