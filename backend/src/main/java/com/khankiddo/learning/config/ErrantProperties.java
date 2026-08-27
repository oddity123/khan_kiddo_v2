package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ERRANT HTTP 批注服务（Stage2 后软依赖，用于 R/M/U 操作高亮）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.errant")
public class ErrantProperties {

    /** 总开关；关闭时不发起任何 HTTP 调用 */
    private boolean enabled = false;

    /** 服务根地址，不含尾斜杠，例如 http://127.0.0.1:8000 */
    private String baseUrl = "http://127.0.0.1:8000";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(10);
}
