package com.khankiddo.learning.memory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryPriorityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryPriorityServiceTest {

    @Mock
    private WeaknessMemoryMapper memoryMapper;
    @Mock
    private ConversationAnalysisMapper analysisMapper;

    private WeaknessMemoryProperties properties;
    private MemoryPriorityService priorityService;

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        PointDictionary dictionary = PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");
        priorityService = new MemoryPriorityService(properties, memoryMapper, analysisMapper, dictionary);
    }

    @Test
    void capsTargetsAtThreeAndIncludesDueMasteredOnly() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        WeaknessMemory m1 = memory(1L, "PAST_SIMPLE_DONE", MemoryType.GRAMMAR, MemoryStatus.ACTIVE, 4, 10, null);
        WeaknessMemory m2 = memory(2L, "NOUN_CLAUSE_ORDER", MemoryType.GRAMMAR, MemoryStatus.ACTIVE, 3, 20, null);
        WeaknessMemory m3 = memory(3L, "PLURAL_COUNTABLE", MemoryType.GRAMMAR, MemoryStatus.PRACTICING, 2, 30, null);
        WeaknessMemory dueMastered = memory(4L, "ARTICLE_A_AN", MemoryType.GRAMMAR,
                MemoryStatus.MASTERED, 1, 90, now.minusHours(1));
        WeaknessMemory futureMastered = memory(5L, "SVA_THIRD_PERSON", MemoryType.GRAMMAR,
                MemoryStatus.MASTERED, 5, 95, now.plusDays(5));

        when(memoryMapper.findByUserId(eq(1L), any()))
                .thenReturn(List.of(m1, m2, m3, dueMastered, futureMastered));

        List<WeaknessMemory> selected = priorityService.selectDailyTargets(1L, Set.of(), 5, now);

        // 上限强制封顶 3
        assertEquals(3, selected.size());
        assertTrue(selected.stream().noneMatch(m -> m.getId().equals(5L)));

        // 若排除前三项，应轮换出到期的 MASTERED 复习项
        List<WeaknessMemory> reviewSelected = priorityService.selectDailyTargets(
                1L, Set.of(1L, 2L, 3L), 2, now);
        assertEquals(1, reviewSelected.size());
        assertEquals(4L, reviewSelected.get(0).getId());
    }

    @Test
    void fluencyHabitIsScaledDownRelativeToGrammar() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        WeaknessMemory habit = memory(1L, "FLUENCY_REDUNDANCY", MemoryType.HABIT, MemoryStatus.ACTIVE, 4, 0, null);
        WeaknessMemory grammar = memory(2L, "SVA_THIRD_PERSON", MemoryType.GRAMMAR, MemoryStatus.ACTIVE, 4, 0, null);

        double habitScore = priorityService.computePriorityScore(habit, now, false, 2);
        double grammarScore = priorityService.computePriorityScore(grammar, now, false, 2);

        assertTrue(grammarScore > habitScore);
    }

    private static WeaknessMemory memory(
            long id,
            String pointId,
            MemoryType type,
            MemoryStatus status,
            int recentHits,
            double mastery,
            LocalDateTime nextReviewAt) {
        return WeaknessMemory.builder()
                .id(id)
                .userId(1L)
                .title(pointId)
                .memoryType(type.name())
                .familyId("FAM_TEST")
                .pointId(pointId)
                .status(status.name())
                .hitCount(recentHits)
                .recentHitCount(recentHits)
                .masteryLevel(BigDecimal.valueOf(mastery))
                .nextReviewAt(nextReviewAt)
                .build();
    }
}
