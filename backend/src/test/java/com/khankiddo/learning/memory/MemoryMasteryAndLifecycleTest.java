package com.khankiddo.learning.memory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.domain.PracticeAttempt;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.MemoryEvidenceMapper;
import com.khankiddo.learning.memory.mapper.PracticeAttemptMapper;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryLifecycleService;
import com.khankiddo.learning.memory.service.MemoryMasteryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryMasteryAndLifecycleTest {

    @Mock
    private WeaknessMemoryMapper memoryMapper;
    @Mock
    private MemoryEvidenceMapper evidenceMapper;
    @Mock
    private PracticeSessionMapper sessionMapper;
    @Mock
    private PracticeAttemptMapper attemptMapper;

    private WeaknessMemoryProperties properties;
    private MemoryLifecycleService lifecycleService;
    private MemoryMasteryService masteryService;

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        lifecycleService = new MemoryLifecycleService(properties);
        masteryService = new MemoryMasteryService(
                properties, memoryMapper, evidenceMapper, sessionMapper, attemptMapper, lifecycleService);
    }

    @Test
    void promotesToMasteredWhenAllCriteriaMet() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(10L)
                .userId(1L)
                .title("一般过去时")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_TENSE")
                .pointId("PAST_SIMPLE_DONE")
                .status(MemoryStatus.PRACTICING.name())
                .hitCount(2)
                .recentHitCount(2)
                .masteryLevel(BigDecimal.ZERO)
                .consecutiveCorrect(0)
                .build();

        when(sessionMapper.findByMemoryId(10L)).thenReturn(List.of(
                PracticeSession.builder().id(100L).analysisId("a1").totalAttempts(3).build(),
                PracticeSession.builder().id(101L).analysisId("a2").totalAttempts(2).build()));
        when(attemptMapper.findByMemoryId(10L)).thenReturn(List.of(
                attempt(1L, 100L, true, true, false, 90, now.minusDays(4)),
                attempt(2L, 100L, true, true, false, 90, now.minusDays(3)),
                attempt(3L, 100L, true, false, true, 70, now.minusDays(2)),
                attempt(4L, 101L, true, true, false, 95, now.minusDays(1)),
                attempt(5L, 101L, true, true, false, 95, now)));
        when(evidenceMapper.findByMemoryId(10L, null)).thenReturn(List.of(
                MemoryEvidence.builder().id(1L).evidenceType(EvidenceType.HIT.name())
                        .createdAt(now.minusDays(10)).build(),
                MemoryEvidence.builder().id(2L).evidenceType(EvidenceType.TRANSFER_SUCCESS.name())
                        .dedupKey("ATTEMPT:101:2").analysisId("a2")
                        .createdAt(now).build()));

        WeaknessMemory updated = masteryService.recalculateAndSave(memory, now);

        assertEquals(MemoryStatus.MASTERED.name(), updated.getStatus());
        assertEquals(5, updated.getConsecutiveCorrect());
        assertEquals(5, updated.getPracticedCount());
        assertEquals(5, updated.getCorrectCount());
        assertNotNull(updated.getNextReviewAt());
        assertEquals(now.plusDays(properties.getReviewIntervalMasteredDays()), updated.getNextReviewAt());
        // P=88, I=80 (4/5), T=50 (1/(1+1)) -> 0.4*88 + 0.3*80 + 0.3*50 = 35.2 + 24 + 15 = 74.20
        assertEquals(new BigDecimal("74.20"), updated.getMasteryLevel());
        verify(memoryMapper).updateMetricsAndStatus(memory);
    }

    @Test
    void doesNotMasterWhenAllCorrectsUsedHints() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(11L)
                .userId(1L)
                .memoryType(MemoryType.GRAMMAR.name())
                .status(MemoryStatus.PRACTICING.name())
                .build();

        when(sessionMapper.findByMemoryId(11L)).thenReturn(List.of(
                PracticeSession.builder().id(200L).totalAttempts(3).build(),
                PracticeSession.builder().id(201L).totalAttempts(2).build()));
        when(attemptMapper.findByMemoryId(11L)).thenReturn(List.of(
                attempt(1L, 200L, true, false, true, 70, now.minusDays(4)),
                attempt(2L, 200L, true, false, true, 70, now.minusDays(3)),
                attempt(3L, 200L, true, false, true, 70, now.minusDays(2)),
                attempt(4L, 201L, true, false, true, 70, now.minusDays(1)),
                attempt(5L, 201L, true, false, true, 70, now)));
        when(evidenceMapper.findByMemoryId(11L, null)).thenReturn(List.of());

        WeaknessMemory updated = masteryService.recalculateAndSave(memory, now);

        assertEquals(MemoryStatus.PRACTICING.name(), updated.getStatus());
    }

    @Test
    void relapseFromMasteredReactivatesToActive() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(12L)
                .status(MemoryStatus.MASTERED.name())
                .consecutiveCorrect(5)
                .build();

        lifecycleService.onHitRecorded(memory, 3, now);

        assertEquals(MemoryStatus.ACTIVE.name(), memory.getStatus());
        assertEquals(0, memory.getConsecutiveCorrect());
        assertEquals(now.plusDays(properties.getReviewIntervalActiveDays()), memory.getNextReviewAt());
    }

    @Test
    void failedPracticeAttemptContributesZeroToMastery() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 0, 15);
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(13L)
                .userId(1L)
                .memoryType(MemoryType.GRAMMAR.name())
                .status(MemoryStatus.PRACTICING.name())
                .build();

        when(sessionMapper.findByMemoryId(13L)).thenReturn(List.of(
                PracticeSession.builder().id(300L).analysisId("a3").totalAttempts(1).build()));
        when(attemptMapper.findByMemoryId(13L)).thenReturn(List.of(
                attempt(1L, 300L, false, false, false, 20, now)));
        when(evidenceMapper.findByMemoryId(13L, null)).thenReturn(List.of());

        WeaknessMemory updated = masteryService.recalculateAndSave(memory, now);

        assertEquals(new BigDecimal("0.00"), updated.getMasteryLevel());
        assertEquals(0, updated.getConsecutiveCorrect());
        assertEquals(0, updated.getCorrectCount());
    }

    private static PracticeAttempt attempt(
            long id,
            long sessionId,
            boolean correct,
            boolean independent,
            boolean hintUsed,
            double score,
            LocalDateTime createdAt) {
        return PracticeAttempt.builder()
                .id(id)
                .sessionId(sessionId)
                .isCorrect(correct)
                .independent(independent)
                .hintUsed(hintUsed)
                .score(BigDecimal.valueOf(score))
                .createdAt(createdAt)
                .build();
    }
}
