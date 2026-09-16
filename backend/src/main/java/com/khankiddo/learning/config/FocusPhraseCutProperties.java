package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 焦点短语切分策略配置：按 pointId 灰度走 LLM（默认空名单 = 全走启发式）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.focus-phrase-cut")
public class FocusPhraseCutProperties {

    /**
     * 命中名单的 pointId 优先走 {@code LlmFocusPhraseCutter}；空 = 今日行为（全启发式）。
     */
    private List<String> llmPointIds = new ArrayList<>();
}
