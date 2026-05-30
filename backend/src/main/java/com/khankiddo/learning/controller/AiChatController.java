package com.khankiddo.learning.controller;

import com.khankiddo.learning.ai.Assistant;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * LLM 连通性测试与 AiService 调用入口。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final Assistant assistant;
    private final ChatModel chatModel;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    /**
     * 通过 AiService 调用（推荐业务层使用此方式）。
     */
    @GetMapping("/chat")
    public Map<String, String> chat(@RequestParam(defaultValue = "用一句话介绍你自己") String message) {
        assertApiKeyConfigured();
        return Map.of(
                "message", message,
                "reply", assistant.chat(message)
        );
    }

    /**
     * 直接使用 ChatModel 调用（用于验证 LangChain4j + 豆包连通性）。
     */
    @GetMapping("/ping")
    public Map<String, String> ping() {
        assertApiKeyConfigured();
        String reply = chatModel.chat("Reply with exactly: pong");
        return Map.of("status", "ok", "reply", reply);
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "未配置 AI_API_KEY 环境变量，无法调用豆包大模型");
        }
    }
}
