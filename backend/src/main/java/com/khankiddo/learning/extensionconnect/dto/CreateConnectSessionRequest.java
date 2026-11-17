package com.khankiddo.learning.extensionconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConnectSessionRequest {

    @NotBlank(message = "codeChallenge 不能为空")
    @Size(min = 43, max = 128, message = "codeChallenge 长度无效")
    private String codeChallenge;
}
