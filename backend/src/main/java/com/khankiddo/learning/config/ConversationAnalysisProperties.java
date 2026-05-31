package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.conversation-analysis")
public class ConversationAnalysisProperties {

    private int minContentLength = 10;
    private int maxContentLength = 10000;
    /** 用户句数超过此值时可扩展分批分析（Phase 1 仍单次调用） */
    private int batchThreshold = 15;

    /** Stage 1 对话分离模型（Flash，对齐 v1 LlmModelKind.FLASH） */
    private String separationModelName = "doubao-seed-1-6-flash-250828";

    /** Stage 1 对话分离 temperature（v1 使用 0.3） */
    private double separationTemperature = 0.3;
}
