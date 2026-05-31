package com.khankiddo.learning.ai.conversation;

import com.khankiddo.learning.ai.conversation.model.SeparationResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Stage 1：对话分离 — LangChain4j 声明式 AI 服务（Flash 模型，见 {@code conversationSeparationChatModel}）。
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "conversationSeparationChatModel")
public interface ConversationSeparationAi {

    @SystemMessage("""
            You are a conversation structure parsing assistant.
            Reply with ONLY valid JSON matching the requested structure. No markdown fences, no extra text.
            """)
    @UserMessage("""
            {{promptTemplate}}

            ---
            Raw subtitles:
            {{rawContent}}
            """)
    SeparationResult separate(@V("promptTemplate") String promptTemplate, @V("rawContent") String rawContent);
}
