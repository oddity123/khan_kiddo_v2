package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.AiLlmProperties;
import com.khankiddo.learning.config.LlmModelProperties;
import com.khankiddo.learning.util.SchemaLoader;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmChatModelFactory {

    private final LlmModelCatalog modelCatalog;
    private final AiLlmProperties aiLlmProperties;
    private final SchemaLoader schemaLoader;
    private final List<GrammarStructuredOutputPolicy> grammarStructuredOutputPolicies;
    private final HttpClientBuilder httpClientBuilder;
    private final Duration defaultTimeout;
    private final Integer defaultMaxRetries;
    private final boolean defaultLogRequests;
    private final boolean defaultLogResponses;

    public LlmChatModelFactory(
            LlmModelCatalog modelCatalog,
            AiLlmProperties aiLlmProperties,
            SchemaLoader schemaLoader,
            List<GrammarStructuredOutputPolicy> grammarStructuredOutputPolicies,
            @Qualifier("openAiChatModelHttpClientBuilder") HttpClientBuilder httpClientBuilder,
            @Value("${langchain4j.open-ai.chat-model.timeout:120s}") Duration defaultTimeout,
            @Value("${langchain4j.open-ai.chat-model.max-retries:3}") Integer defaultMaxRetries,
            @Value("${langchain4j.open-ai.chat-model.log-requests:true}") boolean defaultLogRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses:true}") boolean defaultLogResponses) {
        this.modelCatalog = modelCatalog;
        this.aiLlmProperties = aiLlmProperties;
        this.schemaLoader = schemaLoader;
        this.grammarStructuredOutputPolicies = grammarStructuredOutputPolicies;
        this.httpClientBuilder = httpClientBuilder;
        this.defaultTimeout = defaultTimeout;
        this.defaultMaxRetries = defaultMaxRetries;
        this.defaultLogRequests = defaultLogRequests;
        this.defaultLogResponses = defaultLogResponses;
    }

    private final Map<String, ChatModel> chatCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingCache = new ConcurrentHashMap<>();

    public ChatModel chat(ResolvedLlmModel model) {
        String cacheKey = cacheKey(model);
        return chatCache.computeIfAbsent(cacheKey, key -> buildChatModel(model.getConfig(), null));
    }

    /**
     * Stage2 语法分析：与 {@link #streamingForGrammarAnalysis} 使用相同的结构化输出策略，非流式调用更稳定。
     */
    public ChatModel chatForGrammarAnalysis(ResolvedLlmModel model) {
        GrammarStreamingModelSpec spec = resolveGrammarStreamingSpec(model);
        String cacheKey = cacheKey(model) + spec.getCacheSuffix() + "|chat";
        return chatCache.computeIfAbsent(cacheKey, key -> buildChatModel(model.getConfig(), spec));
    }

    public StreamingChatModel streaming(ResolvedLlmModel model) {
        String cacheKey = cacheKey(model);
        return streamingCache.computeIfAbsent(
                cacheKey, key -> buildStreamingModel(model.getConfig(), null, false, false));
    }

    /**
     * Stage2 语法分析：由结构化输出策略决定 response_format / strict / max_tokens 行为。
     */
    public StreamingChatModel streamingForGrammarAnalysis(ResolvedLlmModel model) {
        GrammarStreamingModelSpec spec = resolveGrammarStreamingSpec(model);
        if (spec.getResponseFormat() == null) {
            return streaming(model);
        }
        String cacheKey = cacheKey(model) + spec.getCacheSuffix();
        return streamingCache.computeIfAbsent(
                cacheKey, key -> buildStreamingModel(model.getConfig(), spec));
    }

    public ResponseFormat grammarAnalysisResponseFormat() {
        return StructuredJsonResponseFormat.fromClasspathSchema(
                StructuredJsonResponseFormat.GRAMMAR_ANALYSIS_SCHEMA_NAME,
                schemaLoader.getConversationAnalysisSchema());
    }

    public ResponseFormat separationResponseFormat() {
        return StructuredJsonResponseFormat.fromClasspathSchema(
                StructuredJsonResponseFormat.SEPARATION_SCHEMA_NAME,
                schemaLoader.getConversationSeparationSchema());
    }

    private String cacheKey(ResolvedLlmModel model) {
        LlmModelProperties.ModelConfig c = model.getConfig();
        return model.getId() + "|"
                + c.getModelName() + "|"
                + normalizeBaseUrl(c.getBaseUrl()) + "|"
                + c.getTemperature() + "|"
                + c.getMaxTokens();
    }

    private ChatModel buildChatModel(LlmModelProperties.ModelConfig config, GrammarStreamingModelSpec spec) {
        Duration timeout = resolveTimeout(config);
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(timeout);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(modelCatalog.resolveApiKey(config))
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(timeout)
                .maxRetries(defaultMaxRetries)
                .logRequests(defaultLogRequests)
                .logResponses(defaultLogResponses)
                .customParameters(aiLlmProperties.thinkingCustomParameters());
        applyGrammarSpec(builder, config, spec);
        return builder.build();
    }

    private void applyGrammarSpec(
            OpenAiChatModel.OpenAiChatModelBuilder builder,
            LlmModelProperties.ModelConfig config,
            GrammarStreamingModelSpec spec) {
        if (spec == null) {
            if (config.getMaxTokens() != null) {
                builder.maxTokens(config.getMaxTokens());
            }
            return;
        }
        if (!spec.isOmitMaxTokens() && config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        if (spec.getResponseFormat() != null) {
            builder.responseFormat(spec.getResponseFormat())
                    .strictJsonSchema(spec.isStrictJsonSchema());
        }
    }

    private StreamingChatModel buildStreamingModel(
            LlmModelProperties.ModelConfig config, GrammarStreamingModelSpec spec) {
        return buildStreamingModel(config, spec.getResponseFormat(), spec.isStrictJsonSchema(), spec.isOmitMaxTokens());
    }

    private StreamingChatModel buildStreamingModel(
            LlmModelProperties.ModelConfig config,
            ResponseFormat responseFormat,
            boolean strictJsonSchema,
            boolean omitMaxTokens) {
        Duration timeout = resolveTimeout(config);
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(timeout);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(modelCatalog.resolveApiKey(config))
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .timeout(timeout)
                .logRequests(defaultLogRequests)
                .logResponses(defaultLogResponses)
                .customParameters(aiLlmProperties.thinkingCustomParameters());
        GrammarStreamingModelSpec spec = GrammarStreamingModelSpec.builder()
                .responseFormat(responseFormat)
                .strictJsonSchema(strictJsonSchema)
                .omitMaxTokens(omitMaxTokens)
                .build();
        applyGrammarSpec(builder, config, spec);
        return builder.build();
    }

    private void applyGrammarSpec(
            OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder,
            LlmModelProperties.ModelConfig config,
            GrammarStreamingModelSpec spec) {
        if (spec == null) {
            if (config.getMaxTokens() != null) {
                builder.maxTokens(config.getMaxTokens());
            }
            return;
        }
        if (!spec.isOmitMaxTokens() && config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        if (spec.getResponseFormat() != null) {
            builder.responseFormat(spec.getResponseFormat())
                    .strictJsonSchema(spec.isStrictJsonSchema());
        }
    }

    private GrammarStreamingModelSpec resolveGrammarStreamingSpec(ResolvedLlmModel model) {
        return grammarStructuredOutputPolicies.stream()
                .filter(policy -> policy.supports(model))
                .findFirst()
                .map(policy -> policy.buildSpec(model))
                .orElseThrow(() -> new IllegalStateException("未找到可用的语法分析结构化输出策略"));
    }

    private Duration resolveTimeout(LlmModelProperties.ModelConfig config) {
        if (config.getTimeout() != null) {
            return config.getTimeout();
        }
        return defaultTimeout;
    }

    private HttpClientBuilder copyHttpClientBuilder(Duration timeout) {
        if (httpClientBuilder instanceof SpringRestClientBuilder springBuilder) {
            Duration connectTimeout = springBuilder.connectTimeout();
            return new SpringRestClientBuilder()
                    .connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(30))
                    .readTimeout(timeout);
        }
        return new SpringRestClientBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(timeout);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://ark.cn-beijing.volces.com/api/v3";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
