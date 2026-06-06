package com.khankiddo.learning.llm;

import com.khankiddo.learning.config.AiLlmProperties;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmChatModelFactory {

    private static final String CACHE_SUFFIX_GRAMMAR_SCHEMA = "|grammar-json-schema";

    private final LlmModelCatalog modelCatalog;
    private final AiLlmProperties aiLlmProperties;
    private final SchemaLoader schemaLoader;
    private final ConversationAnalysisProperties conversationAnalysisProperties;
    private final HttpClientBuilder httpClientBuilder;
    private final Duration defaultTimeout;
    private final Integer defaultMaxRetries;
    private final boolean defaultLogRequests;
    private final boolean defaultLogResponses;

    public LlmChatModelFactory(
            LlmModelCatalog modelCatalog,
            AiLlmProperties aiLlmProperties,
            SchemaLoader schemaLoader,
            ConversationAnalysisProperties conversationAnalysisProperties,
            @Qualifier("openAiChatModelHttpClientBuilder") HttpClientBuilder httpClientBuilder,
            @Value("${langchain4j.open-ai.chat-model.timeout:120s}") Duration defaultTimeout,
            @Value("${langchain4j.open-ai.chat-model.max-retries:3}") Integer defaultMaxRetries,
            @Value("${langchain4j.open-ai.chat-model.log-requests:true}") boolean defaultLogRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses:true}") boolean defaultLogResponses) {
        this.modelCatalog = modelCatalog;
        this.aiLlmProperties = aiLlmProperties;
        this.schemaLoader = schemaLoader;
        this.conversationAnalysisProperties = conversationAnalysisProperties;
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
        return chatCache.computeIfAbsent(cacheKey, key -> buildChatModel(model.getConfig()));
    }

    public StreamingChatModel streaming(ResolvedLlmModel model) {
        String cacheKey = cacheKey(model);
        return streamingCache.computeIfAbsent(cacheKey, key -> buildStreamingModel(model.getConfig(), null));
    }

    /**
     * Stage2 语法分析：带对话分析 JSON Schema 的流式模型（strict 由 {@link ConversationAnalysisProperties} 控制）。
     */
    public StreamingChatModel streamingForGrammarAnalysis(ResolvedLlmModel model) {
        if (!conversationAnalysisProperties.isStrictJsonSchema()) {
            return streaming(model);
        }
        String cacheKey = cacheKey(model) + CACHE_SUFFIX_GRAMMAR_SCHEMA;
        ResponseFormat responseFormat = grammarAnalysisResponseFormat();
        return streamingCache.computeIfAbsent(
                cacheKey, key -> buildStreamingModel(model.getConfig(), responseFormat));
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

    private ChatModel buildChatModel(LlmModelProperties.ModelConfig config) {
        Duration timeout = resolveTimeout(config);
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(timeout);
        return OpenAiChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(modelCatalog.resolveApiKey(config))
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(timeout)
                .maxRetries(defaultMaxRetries)
                .logRequests(defaultLogRequests)
                .logResponses(defaultLogResponses)
                .customParameters(aiLlmProperties.thinkingCustomParameters())
                .build();
    }

    private StreamingChatModel buildStreamingModel(
            LlmModelProperties.ModelConfig config, ResponseFormat responseFormat) {
        Duration timeout = resolveTimeout(config);
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(timeout);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(modelCatalog.resolveApiKey(config))
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(timeout)
                .logRequests(defaultLogRequests)
                .logResponses(defaultLogResponses)
                .customParameters(aiLlmProperties.thinkingCustomParameters());
        if (responseFormat != null) {
            builder.responseFormat(responseFormat)
                    .strictJsonSchema(conversationAnalysisProperties.isStrictJsonSchema());
        }
        return builder.build();
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
