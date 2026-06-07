package com.khankiddo.learning.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class EducationalSummaryClient {

    private final LlmChatModelFactory chatModelFactory;

    public String summarize(String systemPrompt, String userPrompt, ResolvedLlmModel model) {
        ChatModel chatModel = chatModelFactory.chat(model);
        ChatRequest.Builder request = ChatRequest.builder();
        if (StringUtils.hasText(systemPrompt)) {
            request.messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt));
        } else {
            request.messages(UserMessage.from(userPrompt));
        }
        ChatResponse response = chatModel.chat(request.build());
        return response.aiMessage().text();
    }
}
