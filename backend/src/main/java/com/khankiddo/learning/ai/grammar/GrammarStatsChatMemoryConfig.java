package com.khankiddo.learning.ai.grammar;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 语法检索助手的短窗口对话记忆。
 * <p>
 * 进程内 Caffeine 缓存，按 {@code @MemoryId userId} 隔离；
 * 窗口大小、活跃用户上限与闲置淘汰时长见 {@link GrammarStatsProperties.ChatMemory}。
 * 进程重启后清空；后续如需跨重启/多实例共享，再替换为持久化 {@code ChatMemoryStore}。
 */
@Configuration
@RequiredArgsConstructor
public class GrammarStatsChatMemoryConfig {

    public static final String GRAMMAR_STATS_CHAT_MEMORY_PROVIDER =
            "grammarStatsChatMemoryProvider";

    private final GrammarStatsProperties properties;

    @Bean(GRAMMAR_STATS_CHAT_MEMORY_PROVIDER)
    public ChatMemoryProvider grammarStatsChatMemoryProvider() {
        return createProvider(Ticker.systemTicker(), properties.getChatMemory());
    }

    static ChatMemoryProvider createProvider(
            Ticker ticker, GrammarStatsProperties.ChatMemory chatMemory) {
        Cache<Object, ChatMemory> memories = Caffeine.newBuilder()
                .maximumSize(chatMemory.getMaxUsers())
                .expireAfterAccess(Duration.ofHours(chatMemory.getExpireAfterAccessHours()))
                .ticker(ticker)
                .build();
        return memoryId -> memories.get(memoryId, id ->
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(chatMemory.getMaxMessages())
                        .build());
    }
}
