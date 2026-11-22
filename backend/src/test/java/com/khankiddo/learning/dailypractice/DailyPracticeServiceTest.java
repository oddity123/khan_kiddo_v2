package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendResponse;
import com.khankiddo.learning.growth.GrowthCardAnalysisSupport;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.UserWeaknessMemory;
import com.khankiddo.learning.service.conversation.PracticePromptService;
import com.khankiddo.learning.weaknessmemory.WeaknessMemoryConsolidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPracticeServiceTest {

    @Mock
    private UserWeaknessMemoryMapper memoryMapper;
    @Mock
    private ConversationAnalysisMapper analysisMapper;
    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private GrowthCardStore growthCardStore;
    @Mock
    private GrowthCardAnalysisSupport analysisSupport;
    @Mock
    private PracticePromptService practicePromptService;
    @Mock
    private WeaknessMemoryConsolidator consolidator;
    @Mock
    private WeaknessEvidenceSupport evidenceSupport;

    private WeaknessMemoryProperties properties;
    private DailyPracticeService service;

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        properties.setRecommendGoalLimit(2);
        properties.setRecommendVocabLimit(2);
        properties.setCooldownHours(24);
        properties.setCooldownAnalyses(2);
        properties.setDecayHalfLifeDays(21);
        service = new DailyPracticeService(
                properties,
                memoryMapper,
                analysisMapper,
                itemMapper,
                growthCardStore,
                analysisSupport,
                practicePromptService,
                consolidator,
                evidenceSupport);
    }

    @Test
    void prefersNonCoolingMemoryWhenMultipleActive() {
        LocalDateTime nowish = LocalDateTime.now().minusDays(1);
        UserWeaknessMemory cooling = UserWeaknessMemory.builder()
                .id(1L)
                .userId(9L)
                .title("冷却中病灶")
                .hitCount(10)
                .lastSeenAt(nowish)
                .lastPracticedAt(LocalDateTime.now().minusHours(1))
                .status(UserWeaknessMemory.STATUS_ACTIVE)
                .build();
        UserWeaknessMemory ready = UserWeaknessMemory.builder()
                .id(2L)
                .userId(9L)
                .title("可练病灶")
                .hitCount(2)
                .lastSeenAt(nowish)
                .status(UserWeaknessMemory.STATUS_ACTIVE)
                .build();
        when(memoryMapper.findActiveByUserId(9L)).thenReturn(List.of(cooling, ready));
        when(analysisMapper.countByUserIdAndStatusCreatedAfter(eq(9L), eq("success"), any()))
                .thenReturn(0L);
        when(growthCardStore.listDue(eq(9L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceSupport.findLatestEvidence(eq(9L), any())).thenReturn(
                WeaknessEvidenceSupport.EvidenceLine.empty());
        when(practicePromptService.assemble(any())).thenAnswer(inv ->
                com.khankiddo.learning.dto.conversation.PracticePromptResponse.builder()
                        .prompt("PROMPT")
                        .build());

        DailyPracticeRecommendResponse response = service.recommend(9L, null);

        assertFalse(response.isEmpty());
        assertEquals(2L, response.getGoals().get(0).getMemoryId());
        assertTrue(response.getSummary().contains("可练病灶"));
    }

    @Test
    void excludeMemoryIdsRotatesToNext() {
        LocalDateTime seen = LocalDateTime.now().minusDays(1);
        UserWeaknessMemory first = UserWeaknessMemory.builder()
                .id(1L).userId(1L).title("A").hitCount(5).lastSeenAt(seen)
                .status(UserWeaknessMemory.STATUS_ACTIVE).build();
        UserWeaknessMemory second = UserWeaknessMemory.builder()
                .id(2L).userId(1L).title("B").hitCount(4).lastSeenAt(seen)
                .status(UserWeaknessMemory.STATUS_ACTIVE).build();
        when(memoryMapper.findActiveByUserId(1L)).thenReturn(List.of(first, second));
        when(growthCardStore.listDue(eq(1L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceSupport.findLatestEvidence(eq(1L), any())).thenReturn(
                WeaknessEvidenceSupport.EvidenceLine.empty());
        when(practicePromptService.assemble(any())).thenAnswer(inv ->
                com.khankiddo.learning.dto.conversation.PracticePromptResponse.builder()
                        .prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(1L,
                DailyPracticeRecommendRequest.builder().excludeMemoryIds(List.of(1L)).build());

        assertEquals(1, response.getGoals().size());
        assertEquals(2L, response.getGoals().get(0).getMemoryId());
    }

    @Test
    void emptyUserGetsFallbackWithoutColdStartWhenNoHistory() {
        when(memoryMapper.findActiveByUserId(3L)).thenReturn(List.of());
        when(analysisMapper.findByUserId(3L, 1, 0)).thenReturn(List.of());
        when(growthCardStore.listDue(eq(3L), any(LocalDate.class))).thenReturn(List.of());

        DailyPracticeRecommendResponse response = service.recommend(3L, null);

        assertTrue(response.isEmpty());
        assertEquals(DailyPracticeService.EMPTY_FALLBACK_PROMPT, response.getPrompt());
        verify(consolidator, never()).consolidateAfterAnalysis(anyLong(), any());
    }

    @Test
    void diversifiesGoalsToAtMostOneFluencyWhenGrammarAvailable() {
        LocalDateTime seen = LocalDateTime.now().minusDays(1);
        UserWeaknessMemory fluencyHigh = UserWeaknessMemory.builder()
                .id(1L).userId(9L).title("口头重复")
                .anchorHabitKey("FLUENCY_REDUNDANCY").anchorPointId("FLUENCY_REDUNDANCY")
                .hitCount(6).lastSeenAt(seen)
                .status(UserWeaknessMemory.STATUS_ACTIVE).build();
        UserWeaknessMemory fluencyLow = UserWeaknessMemory.builder()
                .id(2L).userId(9L).title("说到一半中断")
                .anchorHabitKey("FLUENCY_INCOMPLETE").anchorPointId("FLUENCY_INCOMPLETE")
                .hitCount(5).lastSeenAt(seen)
                .status(UserWeaknessMemory.STATUS_ACTIVE).build();
        UserWeaknessMemory grammar = UserWeaknessMemory.builder()
                .id(3L).userId(9L).title("宾语从句疑问语序")
                .anchorHabitKey("FAM_STRUCTURE").anchorPointId("NOUN_CLAUSE_ORDER")
                .hitCount(2).lastSeenAt(seen)
                .status(UserWeaknessMemory.STATUS_ACTIVE).build();
        when(memoryMapper.findActiveByUserId(9L)).thenReturn(List.of(fluencyHigh, fluencyLow, grammar));
        when(growthCardStore.listDue(eq(9L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceSupport.findLatestEvidence(eq(9L), any())).thenReturn(
                WeaknessEvidenceSupport.EvidenceLine.empty());
        when(practicePromptService.assemble(any())).thenAnswer(inv ->
                com.khankiddo.learning.dto.conversation.PracticePromptResponse.builder()
                        .prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(9L, null);

        assertEquals(2, response.getGoals().size());
        long fluencyCount = response.getGoals().stream()
                .filter(g -> g.getMemoryId() == 1L || g.getMemoryId() == 2L)
                .count();
        assertEquals(1, fluencyCount);
        assertTrue(response.getGoals().stream().anyMatch(g -> g.getMemoryId() == 3L));
    }

    @Test
    void selectsDueVocabAndExpressionOnly() {
        LocalDateTime seen = LocalDateTime.now().minusDays(1);
        when(memoryMapper.findActiveByUserId(1L)).thenReturn(List.of(
                UserWeaknessMemory.builder()
                        .id(1L).userId(1L).title("G").hitCount(1).lastSeenAt(seen)
                        .status(UserWeaknessMemory.STATUS_ACTIVE).build()));
        when(evidenceSupport.findLatestEvidence(eq(1L), any())).thenReturn(
                WeaknessEvidenceSupport.EvidenceLine.empty());
        when(growthCardStore.listDue(eq(1L), any(LocalDate.class))).thenReturn(List.of(
                GrowthCard.builder().cardId("h1").type("habit").front("h").back("h").build(),
                GrowthCard.builder().cardId("v1").type("vocab").front("vf").back("vb").build(),
                GrowthCard.builder().cardId("e1").type("expression").front("ef").back("eb").build()));
        when(practicePromptService.assemble(any())).thenAnswer(inv ->
                com.khankiddo.learning.dto.conversation.PracticePromptResponse.builder()
                        .prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(1L, null);

        assertEquals(2, response.getVocabulary().size());
        assertEquals("v1", response.getVocabulary().get(0).getCardId());
        assertEquals("e1", response.getVocabulary().get(1).getCardId());
    }
}
