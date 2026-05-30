package com.khankiddo.learning.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginResponse {

    String token;
    String tokenType;
    UserProfileDto user;
}
