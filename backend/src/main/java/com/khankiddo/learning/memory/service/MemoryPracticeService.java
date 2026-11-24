package com.khankiddo.learning.memory.service;

import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.memory.domain.PracticeAttempt;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.dto.PracticeSessionResponse;
import com.khankiddo.learning.memory.dto.RecordPracticeAttemptRequest;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.enums.PracticeSessionStatus;
import com.khankiddo.learning.memory.enums.PracticeType;
import com.khankiddo.learning.memory.mapper.PracticeAttemptMapper;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.util.TextSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 专项练习任务与作答记录服务：串联 Practice Session / Attempt 与 Mastery 更新。
 */
@Service
@RequiredArgsConstructor
public class MemoryPracticeService {

    private final WeaknessMemoryMapper memoryMapper;
    private final PracticeSessionMapper sessionMapper;
    private final PracticeAttemptMapper attemptMapper;
    private final MemoryLifecycleService lifecycleService;
    private final MemoryEvidenceService evidenceService;
    private final MemoryMasteryService masteryService;
    private final Clock clock;

    @Transactional
    public List<PracticeSession> startSessions(
            Long userId,
            List<Long> memoryIds,
            PracticeType practiceType,
            String sourceConversationId) {
        if (userId == null || CollectionUtils.isEmpty(memoryIds)) {
            return List.of();
        }
        List<Long> cleanIds = memoryIds.stream()
                .filter(ObjectUtils::isNotEmpty)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            return List.of();
        }
        List<WeaknessMemory> ownedMemories = memoryMapper.findByUserIdAndIds(userId, cleanIds);
        if (CollectionUtils.isEmpty(ownedMemories)) {
            return List.of();
        }

        PracticeType resolvedType = practiceType != null ? practiceType : PracticeType.SCENARIO;
        String cleanConvId = TextSupport.trimToNull(sourceConversationId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<PracticeSession> created = new ArrayList<>(ownedMemories.size());

        for (WeaknessMemory memory : ownedMemories) {
            PracticeSession session = PracticeSession.builder()
                    .userId(userId)
                    .memoryId(memory.getId())
                    .practiceType(resolvedType.name())
                    .targetPointId(memory.getPointId())
                    .status(PracticeSessionStatus.IN_PROGRESS.name())
                    .sourceConversationId(cleanConvId)
                    .totalAttempts(0)
                    .correctAttempts(0)
                    .startedAt(now)
                    .build();
            sessionMapper.insert(session);
            created.add(session);

            lifecycleService.onPracticeStarted(memory, now);
            int practicedCount = (int) sessionMapper.countByMemoryId(memory.getId());
            memory.setPracticedCount(practicedCount);
            memoryMapper.updateMetricsAndStatus(memory);
        }
        return created;
    }

    @Transactional
    public int bindConversation(Long userId, List<Long> sessionIds, String sourceConversationId) {
        if (userId == null || CollectionUtils.isEmpty(sessionIds) || !StringUtils.hasText(sourceConversationId)) {
            return 0;
        }
        List<Long> cleanIds = sessionIds.stream()
                .filter(ObjectUtils::isNotEmpty)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            return 0;
        }
        return sessionMapper.bindSourceConversation(userId, cleanIds, sourceConversationId.trim());
    }

    @Transactional
    public PracticeSessionResponse recordAttempt(
            Long userId,
            Long sessionId,
            RecordPracticeAttemptRequest request) {
        PracticeSession session = sessionMapper.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BadRequestException("练习会话不存在或无权访问"));
        WeaknessMemory memory = memoryMapper.findByIdAndUserId(session.getMemoryId(), userId)
                .orElseThrow(() -> new BadRequestException("关联薄弱点记忆不存在"));

        LocalDateTime now = LocalDateTime.now(clock);
        int nextNo = attemptMapper.maxAttemptNo(sessionId) + 1;
        boolean correct = Boolean.TRUE.equals(request.getIsCorrect());
        boolean independent = Boolean.TRUE.equals(request.getIndependent());
        boolean hintUsed = Boolean.TRUE.equals(request.getHintUsed());
        BigDecimal score = request.getScore() != null
                ? request.getScore()
                : BigDecimal.valueOf(correct ? 100 : 0);

        PracticeAttempt attempt = PracticeAttempt.builder()
                .sessionId(sessionId)
                .attemptNo(nextNo)
                .userResponse(TextSupport.trimToNull(request.getUserResponse()))
                .expectedResponse(TextSupport.trimToNull(request.getExpectedResponse()))
                .isCorrect(correct)
                .independent(independent)
                .hintUsed(hintUsed)
                .score(score)
                .feedback(TextSupport.trimToNull(request.getFeedback()))
                .evaluationJson(TextSupport.trimToNull(request.getEvaluationJson()))
                .createdAt(now)
                .build();
        attemptMapper.insert(attempt);

        int total = (session.getTotalAttempts() == null ? 0 : session.getTotalAttempts()) + 1;
        int correctCount = (session.getCorrectAttempts() == null ? 0 : session.getCorrectAttempts())
                + (correct ? 1 : 0);
        session.setTotalAttempts(total);
        session.setCorrectAttempts(correctCount);
        sessionMapper.updateCompletion(session);

        EvidenceType evidenceType;
        if (!correct) {
            evidenceType = EvidenceType.PRACTICE_ERROR;
        } else if (independent && !hintUsed) {
            evidenceType = EvidenceType.TRANSFER_SUCCESS;
        } else {
            evidenceType = EvidenceType.PROMPTED_CORRECT;
        }
        evidenceService.recordEvidence(
                memory.getId(),
                userId,
                session.getAnalysisId(),
                null,
                evidenceType,
                MemoryEvidenceService.buildAttemptDedupKey(sessionId, nextNo),
                request.getUserResponse(),
                request.getExpectedResponse(),
                null,
                now);

        masteryService.recalculateAndSave(memory, now);

        List<PracticeAttempt> allAttempts = attemptMapper.findBySessionId(sessionId);
        return MemoryService.toSessionResponse(session, allAttempts);
    }

    public List<PracticeSessionResponse> listSessions(Long userId, Long memoryId, String rawStatus) {
        String statusFilter = null;
        if (StringUtils.hasText(rawStatus)) {
            try {
                statusFilter = PracticeSessionStatus.fromString(rawStatus).name();
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("不支持的会话状态: " + rawStatus);
            }
        }
        List<PracticeSession> sessions = sessionMapper.findByUserId(userId, memoryId, statusFilter);
        List<PracticeSessionResponse> responses = new ArrayList<>();
        if (!CollectionUtils.isEmpty(sessions)) {
            for (PracticeSession session : sessions) {
                List<PracticeAttempt> attempts = attemptMapper.findBySessionId(session.getId());
                responses.add(MemoryService.toSessionResponse(session, attempts));
            }
        }
        return responses;
    }
}
