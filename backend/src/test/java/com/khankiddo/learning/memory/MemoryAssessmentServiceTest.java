package com.khankiddo.learning.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.assessment.MemoryAssessmentLlmClient;
import com.khankiddo.learning.memory.assessment.MemoryAssessmentOutcome;
import com.khankiddo.learning.memory.assessment.MemoryAssessmentResult;
import com.khankiddo.learning.memory.assessment.MemoryAssessmentService;
import com.khankiddo.learning.memory.domain.PracticeAttempt;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.enums.PracticeSessionStatus;
import com.khankiddo.learning.memory.mapper.PracticeAttemptMapper;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryEvidenceService;
import com.khankiddo.learning.memory.service.MemoryMasteryService;
import com.khankiddo.learning.memory.service.MemoryPriorityService;
import com.khankiddo.learning.model.ConversationAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryAssessmentServiceTest {

    @Mock
    private ConversationAnalysisMapper analysisMapper;
    @Mock
    private ConversationAnalysisItemMapper itemMapper;
    @Mock
    private WeaknessMemoryMapper memoryMapper;
    @Mock
    private PracticeSessionMapper sessionMapper;
    @Mock
    private PracticeAttemptMapper attemptMapper;
    @Mock
    private MemoryPriorityService priorityService;
    @Mock
    private MemoryEvidenceService evidenceService;
    @Mock
    private MemoryMasteryService masteryService;
    @Mock
    private MemoryAssessmentLlmClient llmClient;

    private WeaknessMemoryProperties properties;
    private MemoryAssessmentService assessmentService;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        assessmentService = new MemoryAssessmentService(
                properties,
                analysisMapper,
                itemMapper,
                memoryMapper,
                sessionMapper,
                attemptMapper,
                priorityService,
                evidenceService,
                masteryService,
                llmClient,
                new ObjectMapper(),
                null,
                fixedClock);
    }

    @Test
    void boundSessionPracticeErrorRecordsAttemptWithoutTouchingHitCount() {
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(10L)
                .userId(1L)
                .title("一般过去时")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_TENSE")
                .pointId("PAST_SIMPLE_DONE")
                .status(MemoryStatus.PRACTICING.name())
                .hitCount(2)
                .build();
        PracticeSession session = PracticeSession.builder()
                .id(55L)
                .userId(1L)
                .memoryId(10L)
                .targetPointId("PAST_SIMPLE_DONE")
                .status(PracticeSessionStatus.IN_PROGRESS.name())
                .sourceConversationId("conv-abc")
                .totalAttempts(0)
                .correctAttempts(0)
                .build();

        when(sessionMapper.findIdsByUserIdAndAnalysisId(1L, "ana-1")).thenReturn(List.of());
        when(analysisMapper.findByAnalysisIdAndUserId("ana-1", 1L)).thenReturn(Optional.of(
                ConversationAnalysis.builder()
                        .analysisId("ana-1")
                        .conversationContent("User: Yesterday I go to work by bus.")
                        .build()));
        when(sessionMapper.findInProgressByUserIdAndSourceConversation(1L, "conv-abc"))
                .thenReturn(List.of(session));
        when(sessionMapper.completeIfInProgress(session)).thenReturn(1);
        when(itemMapper.findByAnalysisId("ana-1")).thenReturn(List.of());
        when(memoryMapper.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(memory));
        when(priorityService.selectDailyTargets(eq(1L), any(), anyInt(), any())).thenReturn(List.of());
        when(attemptMapper.maxAttemptNo(55L)).thenReturn(0);

        MemoryAssessmentResult.EvaluationDto eval = new MemoryAssessmentResult.EvaluationDto();
        eval.setTargetPointId("PAST_SIMPLE_DONE");
        eval.setOutcome(MemoryAssessmentOutcome.PRACTICE_ERROR.name());
        eval.setIsCorrect(false);
        eval.setIndependent(false);
        eval.setHintUsed(false);
        eval.setScore(20.0);
        eval.setEvidenceQuote("Yesterday I go to work by bus.");
        eval.setExpectedResponse("Yesterday I went to work by bus.");
        eval.setFeedback("过去时间仍用了原形 go");
        when(llmClient.evaluate(any())).thenReturn(List.of(eval));

        assessmentService.assessAfterAnalysis(1L, "ana-1", "conv-abc");

        ArgumentCaptor<PracticeAttempt> attemptCaptor = ArgumentCaptor.forClass(PracticeAttempt.class);
        verify(attemptMapper).insert(attemptCaptor.capture());
        assertFalse(attemptCaptor.getValue().getIsCorrect());
        assertEquals("Yesterday I go to work by bus.", attemptCaptor.getValue().getUserResponse());

        verify(sessionMapper).completeIfInProgress(session);
        assertEquals(PracticeSessionStatus.COMPLETED.name(), session.getStatus());
        assertEquals("ana-1", session.getAnalysisId());

        verify(evidenceService).recordEvidence(
                eq(10L), eq(1L), eq("ana-1"), eq(null),
                eq(EvidenceType.PRACTICE_ERROR), eq("ATTEMPT:55:1"),
                eq("Yesterday I go to work by bus."), eq("Yesterday I went to work by bus."),
                any(BigDecimal.class), any(LocalDateTime.class));
        verify(masteryService).recalculateAndSave(eq(memory), any(LocalDateTime.class));
        assertEquals(2, memory.getHitCount());
    }

    @Test
    void rejectsUnknownPointIdAndUngroundedQuote() {
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(10L)
                .userId(1L)
                .memoryType(MemoryType.GRAMMAR.name())
                .pointId("PAST_SIMPLE_DONE")
                .status(MemoryStatus.ACTIVE.name())
                .build();
        when(sessionMapper.findIdsByUserIdAndAnalysisId(1L, "ana-2")).thenReturn(List.of());
        when(analysisMapper.findByAnalysisIdAndUserId("ana-2", 1L)).thenReturn(Optional.of(
                ConversationAnalysis.builder()
                        .analysisId("ana-2")
                        .conversationContent("User: Today is sunny and warm.")
                        .build()));
        when(itemMapper.findByAnalysisId("ana-2")).thenReturn(List.of());
        when(priorityService.selectDailyTargets(eq(1L), any(), anyInt(), any())).thenReturn(List.of(memory));

        MemoryAssessmentResult.EvaluationDto unknown = new MemoryAssessmentResult.EvaluationDto();
        unknown.setTargetPointId("FAKE_POINT_ID");
        unknown.setOutcome("TRANSFER_SUCCESS");
        unknown.setEvidenceQuote("Today is sunny and warm.");

        MemoryAssessmentResult.EvaluationDto hallucinated = new MemoryAssessmentResult.EvaluationDto();
        hallucinated.setTargetPointId("PAST_SIMPLE_DONE");
        hallucinated.setOutcome("TRANSFER_SUCCESS");
        hallucinated.setEvidenceQuote("Yesterday I visited Paris.");

        when(llmClient.evaluate(any())).thenReturn(List.of(unknown, hallucinated));

        assessmentService.assessAfterAnalysis(1L, "ana-2", null);

        verify(evidenceService, never()).recordEvidence(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(masteryService, never()).recalculateAndSave(any(), any());
    }

    @Test
    void missingSourceConversationIdDoesNotGuessUnboundSessions() {
        when(sessionMapper.findIdsByUserIdAndAnalysisId(1L, "ana-3")).thenReturn(List.of());
        when(analysisMapper.findByAnalysisIdAndUserId("ana-3", 1L)).thenReturn(Optional.of(
                ConversationAnalysis.builder()
                        .analysisId("ana-3")
                        .conversationContent("User: I love coffee.")
                        .build()));
        when(itemMapper.findByAnalysisId("ana-3")).thenReturn(List.of());
        when(priorityService.selectDailyTargets(eq(1L), any(), anyInt(), any())).thenReturn(List.of());

        assessmentService.assessAfterAnalysis(1L, "ana-3", null);

        verify(sessionMapper, never()).findInProgressByUserIdAndSourceConversation(any(), any());
        verify(sessionMapper, never()).completeIfInProgress(any());
        verify(attemptMapper, never()).insert(any());
    }

    @Test
    void alreadyAssessedAnalysisIdIsSkippedIdempotently() {
        when(sessionMapper.findIdsByUserIdAndAnalysisId(1L, "ana-done")).thenReturn(List.of(55L));

        assessmentService.assessAfterAnalysis(1L, "ana-done", "conv-abc");

        verify(analysisMapper, never()).findByAnalysisIdAndUserId(any(), any());
        verify(llmClient, never()).evaluate(any());
        verify(attemptMapper, never()).insert(any());
    }

    @Test
    void concurrentDuplicateCompletionSkipsAttemptAndEvidence() {
        WeaknessMemory memory = WeaknessMemory.builder()
                .id(10L)
                .userId(1L)
                .memoryType(MemoryType.GRAMMAR.name())
                .pointId("PAST_SIMPLE_DONE")
                .status(MemoryStatus.PRACTICING.name())
                .build();
        PracticeSession session = PracticeSession.builder()
                .id(88L)
                .userId(1L)
                .memoryId(10L)
                .targetPointId("PAST_SIMPLE_DONE")
                .status(PracticeSessionStatus.IN_PROGRESS.name())
                .sourceConversationId("conv-race")
                .totalAttempts(0)
                .correctAttempts(0)
                .build();

        when(sessionMapper.findIdsByUserIdAndAnalysisId(1L, "ana-race")).thenReturn(List.of());
        when(analysisMapper.findByAnalysisIdAndUserId("ana-race", 1L)).thenReturn(Optional.of(
                ConversationAnalysis.builder()
                        .analysisId("ana-race")
                        .conversationContent("User: Yesterday I went home early.")
                        .build()));
        when(sessionMapper.findInProgressByUserIdAndSourceConversation(1L, "conv-race"))
                .thenReturn(List.of(session));
        when(sessionMapper.completeIfInProgress(session)).thenReturn(0);
        when(itemMapper.findByAnalysisId("ana-race")).thenReturn(List.of());
        when(memoryMapper.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(memory));
        when(priorityService.selectDailyTargets(eq(1L), any(), anyInt(), any())).thenReturn(List.of());

        MemoryAssessmentResult.EvaluationDto eval = new MemoryAssessmentResult.EvaluationDto();
        eval.setTargetPointId("PAST_SIMPLE_DONE");
        eval.setOutcome(MemoryAssessmentOutcome.TRANSFER_SUCCESS.name());
        eval.setScore(95.0);
        eval.setEvidenceQuote("Yesterday I went home early.");
        when(llmClient.evaluate(any())).thenReturn(List.of(eval));

        assessmentService.assessAfterAnalysis(1L, "ana-race", "conv-race");

        verify(attemptMapper, never()).insert(any());
        verify(evidenceService, never()).recordEvidence(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(masteryService, never()).recalculateAndSave(any(), any());
    }
}
