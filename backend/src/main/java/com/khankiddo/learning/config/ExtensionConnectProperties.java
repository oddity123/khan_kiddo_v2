package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.extension-connect")
public class ExtensionConnectProperties {

    /** 总开关；关闭后连接相关 API 拒绝。 */
    private boolean enabled = true;

    /** 配对会话有效期（分钟）。 */
    private int sessionTtlMinutes = 10;

    /** 扩展专用凭证有效期（天）。 */
    private int credentialTtlDays = 30;

    /** 扩展轮询兑换建议间隔（毫秒），经 config API 暴露。 */
    private long pollIntervalMs = 1500;

    /** 扩展轮询兑换建议总超时（毫秒），经 config API 暴露。 */
    private long pollTimeoutMs = 600_000;
}
