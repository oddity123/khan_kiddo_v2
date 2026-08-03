package com.khankiddo.learning.growth;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 成长卡铸卡助手。{@code @MemoryId userId} 用于经 {@code @ToolMemoryId} 注入
 * {@link GrowthCardTools}（异步线程无 SecurityContext）。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        tools = {"growthCardTools"},
        chatMemoryProvider = GrowthCardMintChatMemoryConfig.GROWTH_CARD_MINT_CHAT_MEMORY_PROVIDER)
public interface GrowthCardMintAssistant {

    String SYSTEM = """
            你是 Khan Kiddo 成长卡铸卡助手。根据提供的「本场 Top1 说话习惯」与原卡证据，
            生成一张可 Anki 复习的练习闪卡（最小可习得点：只练一件事）。
            规则：
            1. 必须调用工具 persist_growth_card 落库，不要只口头描述卡片。
            2. type 固定为 habit；sourceRef 使用提供的 sourceRef；sourceAnalysisId 原样传入。
            3. front：简短中文提示或填空式问题（不要整段报告）。
            4. back：地道英文目标说法 + 必要时一行原句对照。
            5. 不得编造未提供的原句；无足够证据时说明并不要调用工具。
            """;

    @SystemMessage(SYSTEM)
    String mintHabitCard(@MemoryId Long userId, @UserMessage String mintBrief);
}
