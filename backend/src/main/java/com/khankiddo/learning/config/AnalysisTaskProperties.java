package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.analysis-task")
public class AnalysisTaskProperties {

    private boolean enabled = true;

    /** 扩展轮询建议间隔（毫秒），经 config 暴露。 */
    private long pollIntervalMs = 2000;

    /** 扩展轮询建议总超时（毫秒）。 */
    private long pollTimeoutMs = 900_000;
}
