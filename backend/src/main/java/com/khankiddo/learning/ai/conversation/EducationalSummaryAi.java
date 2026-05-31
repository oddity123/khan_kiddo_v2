package com.khankiddo.learning.ai.conversation;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * Stage 3：教育性总结 — LangChain4j 声明式 AI 服务（Markdown 输出）。
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "openAiChatModel")
public interface EducationalSummaryAi {

    @UserMessage("{{prompt}}")
    String summarize(@V("prompt") String prompt);
}
