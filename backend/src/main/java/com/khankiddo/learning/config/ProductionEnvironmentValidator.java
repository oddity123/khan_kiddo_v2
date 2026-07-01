package com.khankiddo.learning.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * prod profile 启动时校验必填环境变量，避免开发默认值带入生产。
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionEnvironmentValidator implements ApplicationRunner {

    private static final String DEV_JWT_SECRET = "khankiddo-v2-dev-jwt-secret-change-me-in-production";
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        requireText("DB_URL", "数据库连接串");
        requireText("DB_PASSWORD", "数据库密码");
        validateJwtSecret();
        requireText("DOUBAO_API_KEY", "豆包 API Key");
    }

    private void requireText(String property, String label) {
        String value = environment.getProperty(property);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "生产环境未配置 " + property + "（" + label + "），请通过环境变量注入后重启");
        }
    }

    private void validateJwtSecret() {
        String jwtSecret = environment.getProperty("JWT_SECRET");
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException(
                    "生产环境未配置 JWT_SECRET，请设置至少 " + MIN_JWT_SECRET_LENGTH + " 字符的随机串");
        }
        if (DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("生产环境不能使用开发默认 JWT_SECRET，请更换为强随机串");
        }
        if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足 " + MIN_JWT_SECRET_LENGTH + " 字符，请更换为更强的密钥");
        }
    }
}
