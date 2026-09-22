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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmChatModelFactory {

    private final LlmModelCatalog modelCatalog;
    private final AiLlmProperties aiLlmProperties;
    private final SchemaLoader schemaLoader;
    private final List<GrammarStructuredOutputPolicy> grammarStructuredOutputPolicies;
    private final PhraseCardReviewOutputPolicy phraseCardReviewOutputPolicy;
    private final HttpClientBuilder httpClientBuilder;
    private final Duration defaultChatTimeout;
    private final Duration defaultStreamingTimeout;
    private final Integer defaultMaxRetries;
    private final boolean defaultLogRequests;
    private final boolean defaultLogResponses;

    public LlmChatModelFactory(
            LlmModelCatalog modelCatalog,
            AiLlmProperties aiLlmProperties,
            ConversationAnalysisProperties conversationAnalysisProperties,
            SchemaLoader schemaLoader,
            List<GrammarStructuredOutputPolicy> grammarStructuredOutputPolicies,
            PhraseCardReviewOutputPolicy phraseCardReviewOutputPolicy,
            @Qualifier("openAiChatModelHttpClientBuilder") HttpClientBuilder httpClientBuilder,
            @Value("${langchain4j.open-ai.chat-model.max-retries:1}") Integer defaultMaxRetries,
            @Value("${langchain4j.open-ai.chat-model.log-requests:true}") boolean defaultLogRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses:true}") boolean defaultLogResponses) {
        this.modelCatalog = modelCatalog;
        this.aiLlmProperties = aiLlmProperties;
        this.schemaLoader = schemaLoader;
        this.grammarStructuredOutputPolicies = grammarStructuredOutputPolicies;
        this.phraseCardReviewOutputPolicy = phraseCardReviewOutputPolicy;
        this.httpClientBuilder = httpClientBuilder;
        this.defaultChatTimeout = conversationAnalysisProperties.getChatTimeout();
        this.defaultStreamingTimeout = conversationAnalysisProperties.getHttpReadTimeout();
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
     * Stage2 语法分析：按结构化输出策略配置 response_format / strict / max_tokens。
     */
    public ChatModel chatForGrammarAnalysis(ResolvedLlmModel model) {
        GrammarStreamingModelSpec spec = resolveGrammarStreamingSpec(model);
        String cacheKey = cacheKey(model) + spec.getCacheSuffix() + "|chat";
        return chatCache.computeIfAbsent(cacheKey, key -> buildChatModel(model.getConfig(), spec));
    }

    /**
     * Phrase Review（中英统一）：按模型选择 json_schema 或 json_object（DeepSeek 仅后者）。
     */
    public ChatModel chatForPhraseCardReview(ResolvedLlmModel model) {
        GrammarStreamingModelSpec spec = phraseCardReviewOutputPolicy.buildSpec(model);
        String cacheKey = cacheKey(model) + "|phrase-review" + spec.getCacheSuffix() + "|chat";
        return chatCache.computeIfAbsent(cacheKey, key -> buildChatModel(model.getConfig(), spec));
    }

    public StreamingChatModel streaming(ResolvedLlmModel model) {
        String cacheKey = cacheKey(model);
        return streamingCache.computeIfAbsent(
                cacheKey, key -> buildStreamingModel(model.getConfig(), null, false, false));
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
                + LlmEndpointSupport.normalizeDoubaoBaseUrl(c.getBaseUrl()) + "|"
                + c.getTemperature() + "|"
                + c.getMaxTokens();
    }

    private ChatModel buildChatModel(LlmModelProperties.ModelConfig config, GrammarStreamingModelSpec spec) {
        Duration timeout = resolveChatTimeout(config);
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(timeout);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(LlmEndpointSupport.normalizeDoubaoBaseUrl(config.getBaseUrl()))
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
            LlmModelProperties.ModelConfig config,
            ResponseFormat responseFormat,
            boolean strictJsonSchema,
            boolean omitMaxTokens) {
        Duration timeout = defaultStreamingTimeout;
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(timeout);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(LlmEndpointSupport.normalizeDoubaoBaseUrl(config.getBaseUrl()))
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

    private Duration resolveChatTimeout(LlmModelProperties.ModelConfig config) {
        if (config.getTimeout() != null) {
            return config.getTimeout();
        }
        return defaultChatTimeout;
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

}
