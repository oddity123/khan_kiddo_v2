package com.khankiddo.learning.config;

import com.khankiddo.learning.llm.LlmChatModelFactory;
import com.khankiddo.learning.llm.LlmEndpointSupport;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Stage1 对话分离专用 ChatModel（与 starter 默认 Bean 共用 model-name / temperature，
 * 另挂 JSON Schema + chat-timeout）。
 * <p>
 * 默认 {@code openAiChatModel} / {@code openAiStreamingChatModel} 由 starter 自动配置。
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
            @Value("${langchain4j.open-ai.chat-model.model-name:doubao-seed-1-8-251228}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.2}") double temperature,
            @Value("${langchain4j.open-ai.chat-model.max-tokens:10240}") Integer maxTokens,
            @Value("${langchain4j.open-ai.chat-model.max-retries:1}") Integer maxRetries,
            @Value("${langchain4j.open-ai.chat-model.log-requests:true}") boolean logRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses:true}") boolean logResponses) {

        Duration chatTimeout = conversationAnalysisProperties.getChatTimeout();
        HttpClientBuilder clientBuilder = copyHttpClientBuilder(httpClientBuilder, chatTimeout);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(clientBuilder)
                .baseUrl(LlmEndpointSupport.normalizeDoubaoBaseUrl(baseUrl))
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(chatTimeout)
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
}
