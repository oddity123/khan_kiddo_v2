package com.khankiddo.learning.llm;

import com.khankiddo.learning.dto.conversation.ActionCardDiagnosisResultDto;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 3 教育总结 / 行动卡诊断：单次非流式 chat，不走流式预览。
 */
@Component
@RequiredArgsConstructor
public class EducationalSummaryClient {

    private final LlmChatModelFactory chatModelFactory;

    public ActionCardDiagnosisResultDto diagnose(String systemPrompt, String userPrompt, ResolvedLlmModel model) {
        ChatModel chatModel = chatModelFactory.chat(model);
        ActionCardDiagnosisAssistant assistant = AiServices.builder(ActionCardDiagnosisAssistant.class)
                .chatModel(chatModel)
                .build();
        return assistant.diagnose(systemPrompt, userPrompt);
    }
}
