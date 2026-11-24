package com.khankiddo.learning.memory.service;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.knowledge.PointChannel;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.domain.PracticeAttempt;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.dto.MemoryDetailResponse;
import com.khankiddo.learning.memory.dto.MemoryEvidenceResponse;
import com.khankiddo.learning.memory.dto.MemoryItemResponse;
import com.khankiddo.learning.memory.dto.MemoryListResponse;
import com.khankiddo.learning.memory.dto.PracticeAttemptResponse;
import com.khankiddo.learning.memory.dto.PracticeSessionResponse;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.MemoryEvidenceMapper;
import com.khankiddo.learning.memory.mapper.PracticeAttemptMapper;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 薄弱点记忆核心服务：基于 (userId, pointId) 确定性聚合分析命中，管理查询/状态修改/级联清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final int DETAIL_EVIDENCE_LIMIT = 30;

    private final WeaknessMemoryProperties properties;
    private final WeaknessMemoryMapper memoryMapper;
    private final MemoryEvidenceMapper evidenceMapper;
    private final PracticeSessionMapper sessionMapper;
    private final PracticeAttemptMapper attemptMapper;
    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;
    private final PointDictionary pointDictionary;
    private final MemoryEvidenceService evidenceService;
    private final MemoryLifecycleService lifecycleService;
    private final MemoryMasteryService masteryService;
    private final MemoryPriorityService priorityService;
    private final Clock clock;

    @Transactional
    public List<Long> recordAnalysisHits(Long userId, String analysisId) {
        if (!properties.isEnabled() || userId == null || !StringUtils.hasText(analysisId)) {
            return List.of();
        }
        String normalizedAnalysisId = analysisId.trim();
        Optional<ConversationAnalysis> analysisOpt =
                analysisMapper.findByAnalysisIdAndUserId(normalizedAnalysisId, userId);
        if (analysisOpt.isEmpty()) {
            log.debug("recordAnalysisHits skipped: analysis not found userId={} analysisId={}",
                    userId, normalizedAnalysisId);
            return List.of();
        }
        List<ConversationAnalysisItem> items = itemMapper.findByAnalysisId(normalizedAnalysisId);
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime analysisTime = analysisOpt.get().getCreatedAt() != null
                ? analysisOpt.get().getCreatedAt()
                : now;

        Set<Long> affectedIds = new LinkedHashSet<>();
        for (ConversationAnalysisItem item : items) {
            if (item == null || !StringUtils.hasText(item.getPointId())) {
                continue;
            }
            PointDefinition point = pointDictionary.pointsById().get(item.getPointId().trim());
            if (point == null || point.channel() == PointChannel.CHINESE) {
                continue;
            }
            WeaknessMemory memory = findOrCreateMemory(userId, point, analysisTime);
            String dedupKey = MemoryEvidenceService.buildHitDedupKey(
                    normalizedAnalysisId, item.getSentenceId(), item.getOriginalSentence());
            String sentenceId = item.getSentenceId() == null ? null : String.valueOf(item.getSentenceId());
            boolean inserted = evidenceService.recordEvidence(
                    memory.getId(),
                    userId,
                    normalizedAnalysisId,
                    sentenceId,
                    EvidenceType.HIT,
                    dedupKey,
                    item.getOriginalSentence(),
                    item.getSuggestion(),
                    null,
                    analysisTime);
            if (inserted) {
                affectedIds.add(memory.getId());
            }
        }

        for (Long memoryId : affectedIds) {
            refreshHitMetricsAndLifecycle(userId, memoryId, now);
        }
        return new ArrayList<>(affectedIds);
    }

    /**
     * 为兜底推荐路径按 (userId, pointId) 幂等创建或获取薄弱点记忆（跳过中文通道及未收录点）。
     */
    @Transactional
    public Optional<WeaknessMemory> ensureMemoryForPoint(Long userId, String pointId) {
        if (!properties.isEnabled() || userId == null || !StringUtils.hasText(pointId)) {
            return Optional.empty();
        }
        PointDefinition point = pointDictionary.pointsById().get(pointId.trim());
        if (point == null || point.channel() == PointChannel.CHINESE) {
            return Optional.empty();
        }
        return Optional.of(findOrCreateMemory(userId, point, LocalDateTime.now(clock)));
    }

    @Transactional
    public void handleAnalysisDeleted(Long userId, String analysisId) {
        if (userId == null || !StringUtils.hasText(analysisId)) {
            return;
        }
        String normalizedAnalysisId = analysisId.trim();
        Set<Long> affectedIds = new LinkedHashSet<>(evidenceService.deleteByAnalysis(userId, normalizedAnalysisId));

        List<Long> sessionIds = sessionMapper.findIdsByUserIdAndAnalysisId(userId, normalizedAnalysisId);
        List<Long> sessionMemoryIds = sessionMapper.findDistinctMemoryIdsByUserIdAndAnalysisId(
                userId, normalizedAnalysisId);
        if (!CollectionUtils.isEmpty(sessionMemoryIds)) {
            affectedIds.addAll(sessionMemoryIds);
        }
        if (!CollectionUtils.isEmpty(sessionIds)) {
            attemptMapper.deleteBySessionIds(sessionIds);
            sessionMapper.deleteByUserIdAndAnalysisId(userId, normalizedAnalysisId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (Long memoryId : affectedIds) {
            reconcileAfterDeletion(userId, memoryId, now);
        }
    }

    public MemoryListResponse listMemories(Long userId, List<String> rawStatuses) {
        List<String> statusFilter = normalizeStatuses(rawStatuses);
        List<WeaknessMemory> all = memoryMapper.findByUserId(userId, null);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MemoryStatus status : MemoryStatus.values()) {
            counts.put(status.name(), 0);
        }
        if (!CollectionUtils.isEmpty(all)) {
            for (WeaknessMemory memory : all) {
                counts.merge(memory.getStatus(), 1, Integer::sum);
            }
        }
        List<WeaknessMemory> filtered = CollectionUtils.isEmpty(statusFilter)
                ? all
                : memoryMapper.findByUserId(userId, statusFilter);
        LocalDateTime now = LocalDateTime.now(clock);
        int poolSize = CollectionUtils.isEmpty(filtered) ? 0 : filtered.size();
        List<MemoryItemResponse> items = new ArrayList<>();
        if (!CollectionUtils.isEmpty(filtered)) {
            for (WeaknessMemory memory : filtered) {
                items.add(toItemResponse(memory, now, poolSize));
            }
        }
        return MemoryListResponse.builder()
                .items(items)
                .countsByStatus(counts)
                .build();
    }

    public MemoryDetailResponse getMemoryDetail(Long userId, Long memoryId) {
        WeaknessMemory memory = requireOwnedMemory(userId, memoryId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<MemoryEvidence> evidences = evidenceMapper.findByMemoryId(memoryId, DETAIL_EVIDENCE_LIMIT);
        List<PracticeSession> sessions = sessionMapper.findByMemoryId(memoryId);

        List<MemoryEvidenceResponse> evidenceDtos = new ArrayList<>();
        if (!CollectionUtils.isEmpty(evidences)) {
            for (MemoryEvidence ev : evidences) {
                evidenceDtos.add(MemoryEvidenceResponse.builder()
                        .id(ev.getId())
                        .analysisId(ev.getAnalysisId())
                        .sentenceId(ev.getSentenceId())
                        .evidenceType(ev.getEvidenceType())
                        .originalText(ev.getOriginalText())
                        .correctedText(ev.getCorrectedText())
                        .confidence(ev.getConfidence())
                        .createdAt(ev.getCreatedAt())
                        .build());
            }
        }

        List<PracticeSessionResponse> sessionDtos = new ArrayList<>();
        if (!CollectionUtils.isEmpty(sessions)) {
            for (PracticeSession session : sessions) {
                List<PracticeAttempt> attempts = attemptMapper.findBySessionId(session.getId());
                sessionDtos.add(toSessionResponse(session, attempts));
            }
        }

        return MemoryDetailResponse.builder()
                .memory(toItemResponse(memory, now, 1))
                .evidences(evidenceDtos)
                .sessions(sessionDtos)
                .build();
    }

    @Transactional
    public MemoryItemResponse updateStatus(Long userId, Long memoryId, String rawStatus) {
        WeaknessMemory memory = requireOwnedMemory(userId, memoryId);
        MemoryStatus target;
        try {
            target = MemoryStatus.fromString(rawStatus);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("不支持的记忆状态: " + rawStatus);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        lifecycleService.applyManualStatus(memory, target, now);
        memoryMapper.updateMetricsAndStatus(memory);
        return toItemResponse(memory, now, 1);
    }

    @Transactional
    public void deleteMemory(Long userId, Long memoryId) {
        requireOwnedMemory(userId, memoryId);
        attemptMapper.deleteByMemoryId(memoryId);
        sessionMapper.deleteByMemoryId(memoryId);
        evidenceMapper.deleteByMemoryId(memoryId);
        memoryMapper.deleteByIdAndUserId(memoryId, userId);
    }

    public WeaknessMemory requireOwnedMemory(Long userId, Long memoryId) {
        if (memoryId == null) {
            throw new BadRequestException("memoryId 不能为空");
        }
        return memoryMapper.findByIdAndUserId(memoryId, userId)
                .orElseThrow(() -> new BadRequestException("薄弱点记忆不存在或无权访问"));
    }

    public MemoryItemResponse toItemResponse(WeaknessMemory memory, LocalDateTime now, int poolSize) {
        boolean cooling = priorityService.isCoolingDown(memory, now);
        double priority = priorityService.computePriorityScore(memory, now, cooling, Math.max(1, poolSize));
        Optional<MemoryEvidence> latestOpt = evidenceService.findLatestHitOrEvidence(memory.getId());
        return MemoryItemResponse.builder()
                .id(memory.getId())
                .title(memory.getTitle())
                .memoryType(memory.getMemoryType())
                .familyId(memory.getFamilyId())
                .pointId(memory.getPointId())
                .status(memory.getStatus())
                .hitCount(memory.getHitCount())
                .recentHitCount(memory.getRecentHitCount())
                .practicedCount(memory.getPracticedCount())
                .correctCount(memory.getCorrectCount())
                .masteryLevel(memory.getMasteryLevel())
                .consecutiveCorrect(memory.getConsecutiveCorrect())
                .priorityScore(priority)
                .firstSeenAt(memory.getFirstSeenAt())
                .lastSeenAt(memory.getLastSeenAt())
                .lastPracticedAt(memory.getLastPracticedAt())
                .nextReviewAt(memory.getNextReviewAt())
                .latestEvidenceOriginal(latestOpt.map(MemoryEvidence::getOriginalText).orElse(null))
                .latestEvidenceCorrected(latestOpt.map(MemoryEvidence::getCorrectedText).orElse(null))
                .build();
    }

    public static PracticeSessionResponse toSessionResponse(
            PracticeSession session,
            List<PracticeAttempt> attempts) {
        List<PracticeAttemptResponse> attemptDtos = new ArrayList<>();
        if (!CollectionUtils.isEmpty(attempts)) {
            for (PracticeAttempt attempt : attempts) {
                attemptDtos.add(PracticeAttemptResponse.builder()
                        .id(attempt.getId())
                        .attemptNo(attempt.getAttemptNo())
                        .userResponse(attempt.getUserResponse())
                        .expectedResponse(attempt.getExpectedResponse())
                        .isCorrect(attempt.getIsCorrect())
                        .independent(attempt.getIndependent())
                        .hintUsed(attempt.getHintUsed())
                        .score(attempt.getScore())
                        .feedback(attempt.getFeedback())
                        .createdAt(attempt.getCreatedAt())
                        .build());
            }
        }
        return PracticeSessionResponse.builder()
                .id(session.getId())
                .memoryId(session.getMemoryId())
                .practiceType(session.getPracticeType())
                .targetPointId(session.getTargetPointId())
                .status(session.getStatus())
                .sourceConversationId(session.getSourceConversationId())
                .analysisId(session.getAnalysisId())
                .totalAttempts(session.getTotalAttempts())
                .correctAttempts(session.getCorrectAttempts())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .createdAt(session.getCreatedAt())
                .attempts(attemptDtos)
                .build();
    }

    private WeaknessMemory findOrCreateMemory(Long userId, PointDefinition point, LocalDateTime seenAt) {
        String pointId = point.pointId().trim();
        Optional<WeaknessMemory> existing = memoryMapper.findByUserIdAndPointId(userId, pointId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String title = StringUtils.hasText(point.titleZh()) ? point.titleZh().trim() : pointId;
        WeaknessMemory created = WeaknessMemory.builder()
                .userId(userId)
                .title(title)
                .memoryType(MemoryType.fromChannel(point.channel()).name())
                .familyId(point.familyId().trim())
                .pointId(pointId)
                .status(MemoryStatus.NEW.name())
                .hitCount(0)
                .recentHitCount(0)
                .practicedCount(0)
                .correctCount(0)
                .masteryLevel(BigDecimal.ZERO)
                .consecutiveCorrect(0)
                .firstSeenAt(seenAt)
                .lastSeenAt(seenAt)
                .build();
        try {
            memoryMapper.insert(created);
            return created;
        } catch (DataIntegrityViolationException ex) {
            return memoryMapper.findByUserIdAndPointId(userId, pointId)
                    .orElseThrow(() -> ex);
        }
    }

    private void refreshHitMetricsAndLifecycle(Long userId, Long memoryId, LocalDateTime now) {
        Optional<WeaknessMemory> memoryOpt = memoryMapper.findByIdAndUserId(memoryId, userId);
        if (memoryOpt.isEmpty()) {
            return;
        }
        WeaknessMemory memory = memoryOpt.get();
        int totalHits = evidenceMapper.countByMemoryIdAndType(memoryId, EvidenceType.HIT.name());
        LocalDateTime cutoff = now.minusDays(Math.max(1, properties.getRecentWindowDays()));
        int recentHits = evidenceMapper.countByMemoryIdAndTypeSince(memoryId, EvidenceType.HIT.name(), cutoff);
        LocalDateTime latestSeen = evidenceMapper
                .findLatestCreatedAtByMemoryIdAndType(memoryId, EvidenceType.HIT.name())
                .orElse(memory.getLastSeenAt() != null ? memory.getLastSeenAt() : now);

        String previousStatus = memory.getStatus();
        memory.setHitCount(totalHits);
        memory.setRecentHitCount(recentHits);
        memory.setLastSeenAt(latestSeen);
        lifecycleService.onHitRecorded(memory, recentHits, now);
        memoryMapper.updateMetricsAndStatus(memory);

        if (MemoryStatus.MASTERED.name().equals(previousStatus)
                || MemoryStatus.PRACTICING.name().equals(previousStatus)
                || (memory.getPracticedCount() != null && memory.getPracticedCount() > 0)) {
            masteryService.recalculateAndSave(memory, now);
        }
    }

    private void reconcileAfterDeletion(Long userId, Long memoryId, LocalDateTime now) {
        Optional<WeaknessMemory> memoryOpt = memoryMapper.findByIdAndUserId(memoryId, userId);
        if (memoryOpt.isEmpty()) {
            return;
        }
        long remainingEvidences = evidenceMapper.countByMemoryId(memoryId);
        long remainingSessions = sessionMapper.countByMemoryId(memoryId);
        if (remainingEvidences == 0 && remainingSessions == 0) {
            memoryMapper.deleteByIdAndUserId(memoryId, userId);
            return;
        }

        WeaknessMemory memory = memoryOpt.get();
        int totalHits = evidenceMapper.countByMemoryIdAndType(memoryId, EvidenceType.HIT.name());
        LocalDateTime cutoff = now.minusDays(Math.max(1, properties.getRecentWindowDays()));
        int recentHits = evidenceMapper.countByMemoryIdAndTypeSince(memoryId, EvidenceType.HIT.name(), cutoff);
        LocalDateTime latestSeen = evidenceMapper
                .findLatestCreatedAtByMemoryIdAndType(memoryId, EvidenceType.HIT.name())
                .orElse(memory.getFirstSeenAt() != null ? memory.getFirstSeenAt() : now);

        memory.setHitCount(totalHits);
        memory.setRecentHitCount(recentHits);
        memory.setLastSeenAt(latestSeen);
        if (MemoryStatus.ACTIVE.name().equals(memory.getStatus())
                && recentHits < Math.max(1, properties.getConfirmMinHits())
                && remainingSessions == 0) {
            memory.setStatus(MemoryStatus.NEW.name());
            memory.setNextReviewAt(null);
        }
        memoryMapper.updateMetricsAndStatus(memory);
        masteryService.recalculateAndSave(memory, now);
    }

    private static List<String> normalizeStatuses(List<String> rawStatuses) {
        if (CollectionUtils.isEmpty(rawStatuses)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String raw : rawStatuses) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                result.add(MemoryStatus.fromString(raw).name());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("不支持的记忆状态: " + raw);
            }
        }
        return result.isEmpty() ? null : result;
    }
}
