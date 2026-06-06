package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import lombok.Builder;
import lombok.Value;

/**
 * 经目录校验后的模型选择结果，供 Stage2/Stage3 调用与持久化展示。
 */
@Value
@Builder
public class ResolvedLlmModel {

    String id;
    String displayName;
    String provider;
    LlmModelProperties.ModelConfig config;
}
