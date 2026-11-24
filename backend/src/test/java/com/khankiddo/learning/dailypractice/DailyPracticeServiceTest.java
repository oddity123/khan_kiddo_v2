package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.dto.conversation.PracticePromptResponse;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendResponse;
import com.khankiddo.learning.growth.GrowthCardAnalysisSupport;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryEvidenceService;
import com.khankiddo.learning.memory.service.MemoryPracticeService;
import com.khankiddo.learning.memory.service.MemoryPriorityService;
import com.khankiddo.learning.memory.service.MemoryService;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.service.conversation.PracticePromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPracticeServiceTest {

    @Mock
    private WeaknessMemoryMapper memoryMapper;
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
    private MemoryService memoryService;
    @Mock
    private MemoryEvidenceService evidenceService;
    @Mock
    private MemoryPracticeService practiceService;

    private WeaknessMemoryProperties properties;
    private MemoryPriorityService priorityService;
    private DailyPracticeService service;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        properties.setRecommendGoalLimit(2);
        properties.setRecommendVocabLimit(2);
        properties.setCooldownHours(24);
        properties.setCooldownAnalyses(2);

        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        priorityService = new MemoryPriorityService(properties, memoryMapper, analysisMapper, dictionary);
        service = new DailyPracticeService(
                properties,
                memoryMapper,
                analysisMapper,
                itemMapper,
                growthCardStore,
                analysisSupport,
                practicePromptService,
                memoryService,
                priorityService,
                evidenceService,
                practiceService,
                fixedClock);
    }

    @Test
    void prefersNonCoolingMemoryWhenMultipleActive() {
        LocalDateTime now = LocalDateTime.now(fixedClock);
        WeaknessMemory cooling = WeaknessMemory.builder()
                .id(1L)
                .userId(9L)
                .title("冷却中病灶")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_TENSE")
                .pointId("PAST_SIMPLE_DONE")
                .hitCount(10)
                .recentHitCount(5)
                .masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(now.minusDays(1))
                .lastPracticedAt(now.minusHours(1))
                .status(MemoryStatus.ACTIVE.name())
                .build();
        WeaknessMemory ready = WeaknessMemory.builder()
                .id(2L)
                .userId(9L)
                .title("可练病灶")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_STRUCTURE")
                .pointId("NOUN_CLAUSE_ORDER")
                .hitCount(2)
                .recentHitCount(2)
                .masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(now.minusDays(1))
                .status(MemoryStatus.ACTIVE.name())
                .build();

        when(memoryMapper.countAllByUserId(9L)).thenReturn(2L);
        when(memoryMapper.findByUserId(eq(9L), any())).thenReturn(List.of(cooling, ready));
        when(growthCardStore.listDue(eq(9L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceService.findDistinctHitOrEvidence(anyLong(), any())).thenReturn(Optional.empty());
        when(itemMapper.findErrorExamplesByUserId(eq(9L), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
        when(practicePromptService.assemble(any())).thenReturn(
                PracticePromptResponse.builder().prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(9L, null);

        assertFalse(response.isEmpty());
        assertEquals(2L, response.getGoals().get(0).getMemoryId());
        assertTrue(response.getSummary().contains("可练病灶"));
    }

    @Test
    void excludeMemoryIdsRotatesToNext() {
        LocalDateTime seen = LocalDateTime.now(fixedClock).minusDays(1);
        WeaknessMemory first = WeaknessMemory.builder()
                .id(1L).userId(1L).title("A").memoryType("GRAMMAR")
                .familyId("FAM_TENSE").pointId("PAST_SIMPLE_DONE")
                .hitCount(5).recentHitCount(5).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();
        WeaknessMemory second = WeaknessMemory.builder()
                .id(2L).userId(1L).title("B").memoryType("GRAMMAR")
                .familyId("FAM_ARTICLE").pointId("ARTICLE_A_AN")
                .hitCount(4).recentHitCount(4).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();

        when(memoryMapper.countAllByUserId(1L)).thenReturn(2L);
        when(memoryMapper.findByUserId(eq(1L), any())).thenReturn(List.of(first, second));
        when(growthCardStore.listDue(eq(1L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceService.findDistinctHitOrEvidence(eq(2L), any())).thenReturn(Optional.of(
                MemoryEvidence.builder().originalText("orig").correctedText("corr").build()));
        when(practicePromptService.assemble(any())).thenReturn(
                PracticePromptResponse.builder().prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(1L,
                DailyPracticeRecommendRequest.builder().excludeMemoryIds(List.of(1L)).build());

        assertEquals(1, response.getGoals().size());
        assertEquals(2L, response.getGoals().get(0).getMemoryId());
        assertEquals("orig", response.getGoals().get(0).getEvidenceOriginal());
    }

    @Test
    void emptyUserGetsFallbackWithoutSeedWhenNoHistory() {
        when(memoryMapper.countAllByUserId(3L)).thenReturn(0L);
        when(analysisMapper.findByUserId(3L, 3, 0)).thenReturn(List.of());
        when(analysisMapper.findByUserId(3L, 1, 0)).thenReturn(List.of());
        when(memoryMapper.findByUserId(eq(3L), any())).thenReturn(List.of());
        when(growthCardStore.listDue(eq(3L), any(LocalDate.class))).thenReturn(List.of());

        DailyPracticeRecommendResponse response = service.recommend(3L, null);

        assertTrue(response.isEmpty());
        assertEquals(DailyPracticeService.EMPTY_FALLBACK_PROMPT, response.getPrompt());
        verify(memoryService, never()).recordAnalysisHits(anyLong(), any());
    }

    @Test
    void diversifiesGoalsToAtMostOneFluencyWhenGrammarAvailable() {
        LocalDateTime seen = LocalDateTime.now(fixedClock).minusDays(1);
        WeaknessMemory fluencyHigh = WeaknessMemory.builder()
                .id(1L).userId(9L).title("口头重复")
                .memoryType("HABIT").familyId("FAM_FLUENCY").pointId("FLUENCY_REDUNDANCY")
                .hitCount(6).recentHitCount(6).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();
        WeaknessMemory fluencyLow = WeaknessMemory.builder()
                .id(2L).userId(9L).title("说到一半中断")
                .memoryType("HABIT").familyId("FAM_FLUENCY").pointId("FLUENCY_INCOMPLETE")
                .hitCount(5).recentHitCount(5).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();
        WeaknessMemory grammar = WeaknessMemory.builder()
                .id(3L).userId(9L).title("宾语从句疑问语序")
                .memoryType("GRAMMAR").familyId("FAM_STRUCTURE").pointId("NOUN_CLAUSE_ORDER")
                .hitCount(2).recentHitCount(2).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();

        when(memoryMapper.countAllByUserId(9L)).thenReturn(3L);
        when(memoryMapper.findByUserId(eq(9L), any())).thenReturn(List.of(fluencyHigh, fluencyLow, grammar));
        when(growthCardStore.listDue(eq(9L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceService.findDistinctHitOrEvidence(anyLong(), any())).thenReturn(Optional.empty());
        when(itemMapper.findErrorExamplesByUserId(eq(9L), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
        when(practicePromptService.assemble(any())).thenReturn(
                PracticePromptResponse.builder().prompt("PROMPT").build());

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
        LocalDateTime seen = LocalDateTime.now(fixedClock).minusDays(1);
        when(memoryMapper.countAllByUserId(1L)).thenReturn(1L);
        when(memoryMapper.findByUserId(eq(1L), any())).thenReturn(List.of(
                WeaknessMemory.builder()
                        .id(1L).userId(1L).title("G").memoryType("GRAMMAR")
                        .familyId("FAM_TENSE").pointId("PAST_SIMPLE_DONE")
                        .hitCount(2).recentHitCount(2).masteryLevel(BigDecimal.ZERO)
                        .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build()));
        when(evidenceService.findDistinctHitOrEvidence(eq(1L), any())).thenReturn(Optional.empty());
        when(itemMapper.findErrorExamplesByUserId(eq(1L), any(), any(), anyInt(), any()))
                .thenReturn(List.of());
        when(growthCardStore.listDue(eq(1L), any(LocalDate.class))).thenReturn(List.of(
                GrowthCard.builder().cardId("h1").type("habit").front("h").back("h").build(),
                GrowthCard.builder().cardId("v1").type("vocab").front("vf").back("vb").build(),
                GrowthCard.builder().cardId("e1").type("expression").front("ef").back("eb").build()));
        when(practicePromptService.assemble(any())).thenReturn(
                PracticePromptResponse.builder().prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(1L, null);

        assertEquals(2, response.getVocabulary().size());
        assertEquals("v1", response.getVocabulary().get(0).getCardId());
        assertEquals("e1", response.getVocabulary().get(1).getCardId());
    }

    @Test
    void deduplicatesEvidenceSentenceAcrossGoalsInSameBatch() {
        LocalDateTime seen = LocalDateTime.now(fixedClock).minusDays(1);
        WeaknessMemory g1 = WeaknessMemory.builder()
                .id(1L).userId(5L).title("三单").memoryType("GRAMMAR")
                .familyId("FAM_AGREEMENT").pointId("SVA_THIRD_PERSON")
                .hitCount(5).recentHitCount(5).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();
        WeaknessMemory g2 = WeaknessMemory.builder()
                .id(2L).userId(5L).title("词性").memoryType("GRAMMAR")
                .familyId("FAM_WORD_FORM").pointId("WORD_FORM_POS")
                .hitCount(4).recentHitCount(4).masteryLevel(BigDecimal.ZERO)
                .lastSeenAt(seen).status(MemoryStatus.ACTIVE.name()).build();

        when(memoryMapper.countAllByUserId(5L)).thenReturn(2L);
        when(memoryMapper.findByUserId(eq(5L), any())).thenReturn(List.of(g1, g2));
        when(growthCardStore.listDue(eq(5L), any(LocalDate.class))).thenReturn(List.of());
        when(evidenceService.findDistinctHitOrEvidence(eq(1L), any())).thenReturn(Optional.of(
                MemoryEvidence.builder().originalText("Shared Error Sentence.").correctedText("Fix 1").build()));
        when(evidenceService.findDistinctHitOrEvidence(eq(2L), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.Set<String> used = inv.getArgument(1, java.util.Set.class);
            if (used.contains("shared error sentence.")) {
                return Optional.of(MemoryEvidence.builder()
                        .originalText("Distinct Older Sentence.")
                        .correctedText("Fix 2")
                        .build());
            }
            return Optional.of(MemoryEvidence.builder()
                    .originalText("Shared Error Sentence.")
                    .correctedText("Fix 1")
                    .build());
        });
        when(practicePromptService.assemble(any())).thenReturn(
                PracticePromptResponse.builder().prompt("PROMPT").build());

        DailyPracticeRecommendResponse response = service.recommend(5L, null);

        assertEquals(2, response.getGoals().size());
        assertEquals("Shared Error Sentence.", response.getGoals().get(0).getEvidenceOriginal());
        assertEquals("Distinct Older Sentence.", response.getGoals().get(1).getEvidenceOriginal());
    }
}
