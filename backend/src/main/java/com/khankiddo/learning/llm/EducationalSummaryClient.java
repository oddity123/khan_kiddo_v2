package com.khankiddo.learning.llm;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EducationalSummaryClient {

    private final LlmChatModelFactory chatModelFactory;

    public String summarize(String prompt, ResolvedLlmModel model) {
        ChatModel chatModel = chatModelFactory.chat(model);
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build();
        ChatResponse response = chatModel.chat(request);
        return response.aiMessage().text();
    }
}
