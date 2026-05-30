package com.khankiddo.learning.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * 声明式 AI 服务示例：通过 LangChain4j AiServices 调用豆包大模型。
 */
@AiService
public interface Assistant {

    @SystemMessage("""
            You are Khan Kiddo, a helpful English learning assistant.
            Reply concisely: use Chinese when the user writes in Chinese, English when they write in English.
            """)
    String chat(@UserMessage String userMessage);
}
