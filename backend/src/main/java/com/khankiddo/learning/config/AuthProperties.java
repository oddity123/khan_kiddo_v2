package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String jwtSecret;
    private int jwtExpirationHours = 168;

    public String getEffectiveJwtSecret() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("app.auth.jwt-secret must be configured");
        }
        return jwtSecret;
    }
}
