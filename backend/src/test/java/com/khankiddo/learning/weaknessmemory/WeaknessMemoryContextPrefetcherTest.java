package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.ai.grammar.GrammarLearningDbService;
import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.ActionCardDto;
import com.khankiddo.learning.growth.GrowthCardAnalysisSupport;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryContextPrefetcherTest {

    @Mock
    private ConversationAnalysisMapper analysisMapper;
    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private UserWeaknessMemoryMapper memoryMapper;
    @Mock
    private GrowthCardAnalysisSupport analysisSupport;
    @Mock
    private GrammarLearningDbService dbService;
    @Mock
    private PointDictionary pointDictionary;
    @Mock
    private ObjectProvider<ContentRetriever> retrieverProvider;

    private WeaknessMemoryProperties properties;
    private WeaknessMemoryContextPrefetcher prefetcher;

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        prefetcher = new WeaknessMemoryContextPrefetcher(
                analysisMapper,
                itemMapper,
                memoryMapper,
                analysisSupport,
                dbService,
                pointDictionary,
                properties,
                retrieverProvider);
    }

    @Test
    void returnsEmptyWhenNoActionCards() {
        when(analysisMapper.findByAnalysisIdAndUserId("a-1", 1L))
                .thenReturn(Optional.of(ConversationAnalysis.builder().analysisId("a-1").userId(1L).build()));
        when(itemMapper.findByAnalysisId("a-1")).thenReturn(List.of(
                ConversationAnalysisItem.builder().analysisId("a-1").pointId("X").build()));
        when(analysisSupport.score(any())).thenReturn(
                new HabitCardScorer.HabitScoreResult(null, List.of(), List.of()));

        assertTrue(prefetcher.prefetch(1L, "a-1").isEmpty());
        verify(retrieverProvider, never()).getIfAvailable();
    }

    @Test
    void degradesWithoutQdrantRetriever() {
        when(analysisMapper.findByAnalysisIdAndUserId("a-1", 1L))
                .thenReturn(Optional.of(ConversationAnalysis.builder().analysisId("a-1").userId(1L).build()));
        when(itemMapper.findByAnalysisId("a-1")).thenReturn(List.of(
                ConversationAnalysisItem.builder().analysisId("a-1").pointId("NF_GERUND_OBJECT").build()));
        ActionCardDto card = ActionCardDto.builder()
                .habitKey("FAM_NONFINITE")
                .pointId("NF_GERUND_OBJECT")
                .examples(List.of(ActionCardDto.ExampleDto.builder()
                        .originalSentence("I spend 2 hours watch TV")
                        .suggestion("I spend 2 hours watching TV")
                        .build()))
                .build();
        when(analysisSupport.score(any())).thenReturn(
                new HabitCardScorer.HabitScoreResult(card, List.of(card), List.of()));
        when(memoryMapper.findActiveByUserId(1L)).thenReturn(List.of());
        when(dbService.buildStatsSummary(anyLong(), any(), any(), any()))
                .thenReturn("stats");
        when(itemMapper.findErrorExamplesByUserId(eq(1L), isNull(), any(), anyInt(), eq("a-1")))
                .thenReturn(List.of());
        when(retrieverProvider.getIfAvailable()).thenReturn(null);

        Optional<WeaknessMemoryPrefetchContext> ctx = prefetcher.prefetch(1L, "a-1");
        assertTrue(ctx.isPresent());
        assertTrue(ctx.get().cardEvidences().get(0).similarSnippets().isEmpty());
    }
}
