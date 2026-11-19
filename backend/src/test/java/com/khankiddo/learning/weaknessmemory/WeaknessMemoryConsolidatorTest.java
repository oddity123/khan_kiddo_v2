package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryConsolidatorTest {

    @Mock
    private WeaknessMemoryContextPrefetcher prefetcher;
    @Mock
    private WeaknessMemoryLlmClient llmClient;
    @Mock
    private WeaknessMemoryStore store;

    private WeaknessMemoryProperties properties;
    private WeaknessMemoryConsolidator consolidator;

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        consolidator = new WeaknessMemoryConsolidator(properties, prefetcher, llmClient, store);
    }

    @Test
    void skipsWhenDisabled() {
        properties.setEnabled(false);

        consolidator.consolidateAfterAnalysis(1L, "a-1");

        verify(prefetcher, never()).prefetch(any(), any());
    }

    @Test
    void skipsWhenNoActionCards() {
        when(prefetcher.prefetch(1L, "a-1")).thenReturn(Optional.empty());

        consolidator.consolidateAfterAnalysis(1L, "a-1");

        verify(llmClient, never()).consolidate(any());
        verify(store, never()).applyOperations(any(), any(), any());
    }

    @Test
    void appliesLlmOperations() {
        WeaknessMemoryPrefetchContext context = new WeaknessMemoryPrefetchContext(
                1L,
                "a-1",
                List.of(),
                List.of(ActionCardDto.builder().habitKey("FAM_TENSE").build()),
                List.of());
        when(prefetcher.prefetch(1L, "a-1")).thenReturn(Optional.of(context));
        List<WeaknessMemoryOperation> ops = List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_SKIP, null, null, null, null, null, null, null, null));
        when(llmClient.consolidate(context)).thenReturn(ops);

        consolidator.consolidateAfterAnalysis(1L, "a-1");

        verify(store).applyOperations(eq(1L), eq("a-1"), eq(ops));
    }
}
