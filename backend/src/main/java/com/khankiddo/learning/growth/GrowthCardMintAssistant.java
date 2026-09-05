package com.khankiddo.learning.growth;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 通用知识卡片生成能力：只产出正反面文案，不负责落库。
 * 持久化由调用方（如 {@link GrowthCardMintGateway}）显式写入 {@link GrowthCardStore}。
 * <p>
 * 系统 / 用户提示词来自 {@code templates/prompts/growth-card-mint/}，经 {@code PromptLoader} 注入。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel")
public interface GrowthCardMintAssistant {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{userMessage}}")
    GrowthCardDraft generate(@V("systemPrompt") String systemPrompt,
                             @V("userMessage") String userMessage);
}
