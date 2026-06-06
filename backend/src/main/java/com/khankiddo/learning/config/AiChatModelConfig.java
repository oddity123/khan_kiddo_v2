package com.khankiddo.learning.config;

import com.khankiddo.learning.llm.LlmChatModelFactory;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 对话分析各阶段专用 ChatModel（LangChain4j 手动 Bean，便于按阶段选模型与 temperature）。
 * <p>
 * 默认 {@code openAiChatModel} / {@code openAiStreamingChatModel} 由 starter 自动配置（主分析、总结）。
 */
@Configuration
public class AiChatModelConfig {

    @Bean
    ChatModel conversationSeparationChatModel(
            AiLlmProperties aiLlmProperties,
            ConversationAnalysisProperties conversationAnalysisProperties,
            LlmChatModelFactory llmChatModelFactory,
            @Qualifier("openAiChatModelHttpClientBuilder") HttpClientBuilder httpClientBuilder,
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.max-tokens:10240}") Integer maxTokens,
            @Value("${langchain4j.open-ai.chat-model.timeout:120s}") Duration timeout,
            @Value("${langchain4j.open-ai.chat-model.max-retries:3}") Integer maxRetries,
            @Value("${langchain4j.open-ai.chat-model.log-requests:true}") boolean logRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses:true}") boolean logResponses) {

        HttpClientBuilder clientBuilder = copyHttpClientBuilder(httpClientBuilder, timeout);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(normalizeBaseUrl(baseUrl))
                .apiKey(apiKey)
                .modelName(conversationAnalysisProperties.getSeparationModelName())
                .temperature(conversationAnalysisProperties.getSeparationTemperature())
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customParameters(aiLlmProperties.thinkingCustomParameters());
        if (conversationAnalysisProperties.isStrictJsonSchema()) {
            ResponseFormat responseFormat = llmChatModelFactory.separationResponseFormat();
            builder.responseFormat(responseFormat)
                    .strictJsonSchema(true);
        }
        return builder.build();
    }

    private HttpClientBuilder copyHttpClientBuilder(HttpClientBuilder source, Duration timeout) {
        if (source instanceof SpringRestClientBuilder springBuilder) {
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
