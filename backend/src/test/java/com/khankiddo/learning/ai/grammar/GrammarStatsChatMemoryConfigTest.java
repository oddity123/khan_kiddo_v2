package com.khankiddo.learning.ai.grammar;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.service.spring.AiService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class GrammarStatsChatMemoryConfigTest {

    @Test
    void provider_shouldReuseMemoryPerUserId() {
        ChatMemoryProvider provider = GrammarStatsChatMemoryConfig.createProvider(
                Ticker.systemTicker(), new GrammarStatsProperties().getChatMemory());

        ChatMemory first = provider.get(7L);
        first.add(UserMessage.from("我最近常犯什么错？"));

        ChatMemory again = provider.get(7L);
        ChatMemory other = provider.get(8L);

        assertSame(first, again);
        assertNotSame(first, other);
        assertEquals(1, again.messages().size());
        assertEquals(7L, again.id());
    }

    @Test
    void caffeine_shouldEvictAfterIdleAccessWindow() {
        GrammarStatsProperties.ChatMemory chatMemory = new GrammarStatsProperties().getChatMemory();
        AtomicLong nanos = new AtomicLong();
        ChatMemoryProvider provider = GrammarStatsChatMemoryConfig.createProvider(nanos::get, chatMemory);

        ChatMemory first = provider.get(7L);
        first.add(UserMessage.from("第一轮"));

        nanos.addAndGet(Duration.ofHours(chatMemory.getExpireAfterAccessHours())
                .plusMinutes(1)
                .toNanos());

        ChatMemory afterIdle = provider.get(7L);
        assertNotSame(first, afterIdle);
        assertEquals(0, afterIdle.messages().size());
    }

    @Test
    void assistant_shouldWireGrammarStatsChatMemoryProvider() {
        AiService aiService = GrammarStatsAssistant.class.getAnnotation(AiService.class);

        assertEquals(
                GrammarStatsChatMemoryConfig.GRAMMAR_STATS_CHAT_MEMORY_PROVIDER,
                aiService.chatMemoryProvider());
    }
}
