package com.khankiddo.learning.growth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 铸卡助手的短窗口记忆：主要为满足 LangChain4j {@code @MemoryId} → {@code @ToolMemoryId} 注入，
 * 并非多轮对话。按 userId 隔离，闲置淘汰即可。
 */
@Configuration
public class GrowthCardMintChatMemoryConfig {

    public static final String GROWTH_CARD_MINT_CHAT_MEMORY_PROVIDER =
            "growthCardMintChatMemoryProvider";

    @Bean(GROWTH_CARD_MINT_CHAT_MEMORY_PROVIDER)
    public ChatMemoryProvider growthCardMintChatMemoryProvider() {
        Cache<Object, ChatMemory> memories = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(Duration.ofHours(1))
                .build();
        return memoryId -> memories.get(memoryId, id ->
                MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(4)
                        .build());
    }
}
