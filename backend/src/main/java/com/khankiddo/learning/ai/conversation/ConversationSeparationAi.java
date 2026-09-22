package com.khankiddo.learning.ai.conversation;

import com.khankiddo.learning.ai.conversation.model.SeparationResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Stage 1：对话分离 — LangChain4j 声明式 AI 服务（见 {@code conversationSeparationChatModel}）。
 * <p>
 * 非流式 chat。模型与 temperature 与 {@code langchain4j.open-ai.chat-model} 一致；
 * 结构化输出依赖 Bean 上的 {@code responseFormat} / {@code strictJsonSchema}，
 * 而非 {@code @AiService} 注解自动开启。
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "conversationSeparationChatModel")
public interface ConversationSeparationAi {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("""
            {{promptTemplate}}

            ---
            Raw subtitles:
            {{rawContent}}
            """)
    SeparationResult separate(@V("systemPrompt") String systemPrompt,
                              @V("promptTemplate") String promptTemplate,
                              @V("rawContent") String rawContent);
}
