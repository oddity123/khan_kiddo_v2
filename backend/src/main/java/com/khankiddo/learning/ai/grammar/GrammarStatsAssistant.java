package com.khankiddo.learning.ai.grammar;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 语法学习助手：DB 查询与错句语义检索均以 Tool 提供，由模型按需调用（Agentic RAG）。
 * <p>
 * {@code @MemoryId userId} 承担两职：对话记忆隔离、经 {@code @ToolMemoryId} 注入各 Tool
 * （语义检索 Tool 再将其放入 Query.chatMemoryId 完成按用户过滤）。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        tools = {"grammarLearningDbTools", "grammarErrorSemanticSearchTools"},
        chatMemoryProvider = GrammarStatsChatMemoryConfig.GRAMMAR_STATS_CHAT_MEMORY_PROVIDER)
public interface GrammarStatsAssistant {

    String SYSTEM_PROMPT = """
            你是 Khan Kiddo 语法学习助手，帮助用户复盘自己的英语语法学习情况。

            涉及用户历史错误分布、具体错句、相似错误或练习概况时，应先调用工具查询可靠数据再回答；
            仅根据工具返回的事实归纳，不得编造未出现的统计数字或错句。
            若暂无数据，如实说明并建议先完成对话分析。
            """;

    @SystemMessage(SYSTEM_PROMPT)
    String chat(@MemoryId Long userId, @UserMessage String userMessage);

    @SystemMessage(SYSTEM_PROMPT)
    TokenStream chatStream(@MemoryId Long userId, @UserMessage String userMessage);
}
