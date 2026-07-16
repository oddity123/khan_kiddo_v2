package com.khankiddo.learning.controller;

import com.khankiddo.learning.ai.grammar.GrammarStatsAssistant;
import com.khankiddo.learning.security.SecurityUtils;
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
 * 语法学习助手验通入口（需登录）：DB Tools + 可选语义 RAG Augmentor。
 */
@RestController
@RequestMapping("/api/ai/grammar-stats")
public class GrammarStatsAssistantController {

    private final GrammarStatsAssistant grammarStatsAssistant;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    public GrammarStatsAssistantController(GrammarStatsAssistant grammarStatsAssistant) {
        this.grammarStatsAssistant = grammarStatsAssistant;
    }

    @GetMapping("/chat")
    public Map<String, String> chat(
            @RequestParam(defaultValue = "我最常犯什么语法错误？") String message) {
        assertApiKeyConfigured();
        if (!StringUtils.hasText(message)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入问题");
        }
        Long userId = SecurityUtils.requireUserId();
        return Map.of(
                "message", message.trim(),
                "reply", grammarStatsAssistant.chat(userId, message.trim()));
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "未配置 AI_API_KEY 环境变量，无法调用大模型");
        }
    }
}
