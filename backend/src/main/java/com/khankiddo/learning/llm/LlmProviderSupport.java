package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.LlmModelProperties;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 按供应商 / 端点判断结构化输出能力。
 * <p>
 * 百炼文档：DeepSeek 仅 JSON Object；JSON Schema 仅部分 Qwen 3.7/3.8。
 */
public final class LlmProviderSupport {

    private static final String QWEN_PROVIDER = "qwen";
    private static final String DOUBAO_PROVIDER = "doubao";
    private static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";

    private LlmProviderSupport() {
    }

    public static boolean isDashScopeCompatible(ResolvedLlmModel model) {
        if (model == null) {
            return false;
        }
        if (providerEquals(model.getProvider(), QWEN_PROVIDER)) {
            return true;
        }
        return isDashScopeConfig(model.getConfig());
    }

    public static boolean isDeepSeek(ResolvedLlmModel model) {
        return containsToken(model, "deepseek");
    }

    public static boolean isDoubao(ResolvedLlmModel model) {
        if (model == null) {
            return false;
        }
        if (providerEquals(model.getProvider(), DOUBAO_PROVIDER)) {
            return true;
        }
        return containsToken(model, "doubao");
    }

    /**
     * 是否可把 {@code response_format} 设为 json_schema + strict。
     * DeepSeek（含经百炼转发）只有 json_object。
     */
    public static boolean supportsJsonSchemaResponseFormat(ResolvedLlmModel model) {
        if (model == null) {
            return true;
        }
        if (isDeepSeek(model)) {
            return false;
        }
        if (isDoubao(model)) {
            return true;
        }
        if (isDashScopeCompatible(model)) {
            return isQwenJsonSchemaFamily(modelName(model));
        }
        return true;
    }

    private static boolean isQwenJsonSchemaFamily(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return false;
        }
        String normalized = modelName.toLowerCase(Locale.ROOT);
        return normalized.contains("qwen3.7")
                || normalized.contains("qwen3.8")
                || normalized.contains("qwen3-7")
                || normalized.contains("qwen3-8");
    }

    private static boolean isDashScopeConfig(LlmModelProperties.ModelConfig config) {
        if (config == null) {
            return false;
        }
        if (providerEquals(config.getProvider(), QWEN_PROVIDER)) {
            return true;
        }
        if (StringUtils.hasText(config.getBaseUrl())
                && config.getBaseUrl().toLowerCase(Locale.ROOT).contains(DASHSCOPE_HOST)) {
            return true;
        }
        return StringUtils.hasText(config.getModelName())
                && config.getModelName().toLowerCase(Locale.ROOT).startsWith(QWEN_PROVIDER);
    }

    private static boolean containsToken(ResolvedLlmModel model, String token) {
        if (model == null) {
            return false;
        }
        return containsIgnoreCase(model.getId(), token)
                || containsIgnoreCase(model.getDisplayName(), token)
                || containsIgnoreCase(modelName(model), token);
    }

    private static String modelName(ResolvedLlmModel model) {
        if (model.getConfig() != null && StringUtils.hasText(model.getConfig().getModelName())) {
            return model.getConfig().getModelName();
        }
        return model.getId();
    }

    private static boolean providerEquals(String provider, String expected) {
        return StringUtils.hasText(provider) && expected.equalsIgnoreCase(provider.trim());
    }

    private static boolean containsIgnoreCase(String value, String token) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(token);
    }
}
