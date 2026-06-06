package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import com.khankiddo.learning.dto.conversation.LlmModelOptionDto;
import com.khankiddo.learning.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LlmModelCatalog {

    private final LlmModelProperties properties;

    public ResolvedLlmModel resolveOrDefault(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return resolveRequired(properties.getDefaultModelId());
        }
        return resolveRequired(modelId.trim());
    }

    public ResolvedLlmModel resolveRequired(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            throw new BadRequestException("未配置默认分析模型");
        }
        LlmModelProperties.ModelConfig config = indexById().get(modelId.trim());
        if (ObjectUtils.isEmpty(config)) {
            throw new BadRequestException("不支持的分析模型: " + modelId);
        }
        if (!config.isEnabled()) {
            throw new BadRequestException("分析模型已禁用: " + modelId);
        }
        validateConfig(config);
        return ResolvedLlmModel.builder()
                .id(config.getId())
                .displayName(config.getDisplayName())
                .provider(config.getProvider())
                .config(config)
                .build();
    }

    public List<LlmModelOptionDto> listEnabled() {
        String defaultId = properties.getDefaultModelId();
        return indexById().values().stream()
                .filter(LlmModelProperties.ModelConfig::isEnabled)
                .filter(config -> StringUtils.hasText(resolveApiKey(config)))
                .map(config -> LlmModelOptionDto.builder()
                        .id(config.getId())
                        .displayName(config.getDisplayName())
                        .provider(config.getProvider())
                        .defaultModel(config.getId().equals(defaultId))
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, LlmModelProperties.ModelConfig> indexById() {
        Map<String, LlmModelProperties.ModelConfig> map = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(properties.getModels())) {
            return map;
        }
        for (LlmModelProperties.ModelConfig config : properties.getModels()) {
            if (StringUtils.hasText(config.getId())) {
                map.put(config.getId().trim(), config);
            }
        }
        return map;
    }

    private void validateConfig(LlmModelProperties.ModelConfig config) {
        if (!StringUtils.hasText(config.getModelName())) {
            throw new BadRequestException("模型配置缺少 modelName: " + config.getId());
        }
        if (!StringUtils.hasText(resolveApiKey(config))) {
            throw new BadRequestException("模型未配置 API Key: " + config.getId());
        }
    }

    /**
     * 解析有效 API Key（供工厂构建客户端；不对外暴露）。
     */
    public String resolveApiKey(LlmModelProperties.ModelConfig config) {
        if (StringUtils.hasText(config.getApiKey())) {
            return config.getApiKey().trim();
        }
        if (StringUtils.hasText(config.getApiKeyEnv())) {
            String fromEnv = System.getenv(config.getApiKeyEnv().trim());
            if (StringUtils.hasText(fromEnv)) {
                return fromEnv.trim();
            }
        }
        return null;
    }
}
