package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户可选分析模型目录（{@code app.llm}）。
 * <p>
 * 敏感信息（API Key、可选 base-url / model-name）须在 {@code application.yml} 中用占位符引用环境变量，
 * 例如 {@code ${DOUBAO_API_KEY:}}、{@code ${QWEN_API_KEY:}}，真实值只放在 {@code .env}（勿提交仓库）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmModelProperties {

    /**
     * 前端未传 {@code modelId} 或传空时使用的默认条目 ID，须与 {@link #models} 中某条 {@link ModelConfig#id} 一致。
     */
    private String defaultModelId = "doubao-seed";

    /**
     * 可供用户选择的模型白名单；仅 {@link ModelConfig#enabled} 为 true 且能解析到 API Key 的条目会出现在列表接口中。
     */
    private List<ModelConfig> models = new ArrayList<>();

    /**
     * 单条可调用模型配置，对应 {@code app.llm.models[]} 的一项。
     */
    @Data
    public static class ModelConfig {

        /**
         * 稳定标识，前端提交 {@code modelId}、落库 {@code llm_model_id} 均使用此值。
         */
        private String id;

        /**
         * 界面展示名称（如「Doubao Seed 1.8」），仅用于模型下拉列表等前端展示。
         */
        private String displayName;

        /**
         * 供应商标识（如 doubao、qwen），仅用于展示与审计，落库 {@code llm_provider}。
         */
        private String provider;

        /**
         * OpenAI 兼容 Chat Completions 根地址。
         * YAML 示例：{@code base-url: ${DOUBAO_BASE_URL:https://ark.../api/v3}}。
         */
        private String baseUrl;

        /**
         * API Key（敏感）。YAML 示例：{@code api-key: ${DOUBAO_API_KEY:}}，值仅来自环境变量 / .env。
         */
        private String apiKey;

        /**
         * 当 {@link #apiKey} 绑定后仍为空时，从该环境变量名二次读取（一般不必配置，优先用 YAML 占位符）。
         */
        private String apiKeyEnv;

        /**
         * 厂商侧真实模型 ID，落库 {@code llm_model_name}。
         * YAML 示例：{@code model-name: ${DOUBAO_MODEL_NAME:doubao-seed-1-8-251228}}。
         */
        private String modelName;

        /**
         * 采样温度，未配置时工厂侧默认 0.4。
         */
        private Double temperature = 0.2;

        /**
         * 单次请求最大输出 token 数。
         */
        private Integer maxTokens = 10240;

        /**
         * 单次请求超时；未配置时使用 {@code langchain4j.open-ai.chat-model.timeout}。
         */
        private Duration timeout;

        /**
         * 为 false 时不参与解析与列表接口；为 true 但无有效 Key 时也不会出现在下拉列表。
         */
        private boolean enabled = true;
    }
}
