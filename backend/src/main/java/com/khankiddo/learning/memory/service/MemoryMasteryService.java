package com.khankiddo.learning.memory.service;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.domain.PracticeAttempt;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.EvidenceType;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.MemoryEvidenceMapper;
import com.khankiddo.learning.memory.mapper.PracticeAttemptMapper;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 掌握程度计算引擎：汇总练习表现 P、独立表达 I 与真实语境迁移 T，推进 MASTERED 判定。
 * M = 0.4 * P + 0.3 * I + 0.3 * T（权重由 WeaknessMemoryProperties 单源配置）。
 */
@Service
@RequiredArgsConstructor
public class MemoryMasteryService {

    private final WeaknessMemoryProperties properties;
    private final WeaknessMemoryMapper memoryMapper;
    private final MemoryEvidenceMapper evidenceMapper;
    private final PracticeSessionMapper sessionMapper;
    private final PracticeAttemptMapper attemptMapper;
    private final MemoryLifecycleService lifecycleService;

    @Transactional
    public WeaknessMemory recalculateAndSave(WeaknessMemory memory, LocalDateTime now) {
        if (memory == null || memory.getId() == null) {
            return memory;
        }
        List<PracticeSession> sessions = orEmpty(sessionMapper.findByMemoryId(memory.getId()));
        List<PracticeAttempt> attempts = orEmpty(attemptMapper.findByMemoryId(memory.getId()));
        List<MemoryEvidence> evidences = orEmpty(evidenceMapper.findByMemoryId(memory.getId(), null));

        Set<String> sessionAnalysisIds = new HashSet<>();
        for (PracticeSession session : sessions) {
            if (StringUtils.hasText(session.getAnalysisId())) {
                sessionAnalysisIds.add(session.getAnalysisId().trim());
            }
        }

        List<MemoryEvidence> freeChatPositives = new ArrayList<>();
        List<MemoryEvidence> hits = new ArrayList<>();
        List<MemoryEvidence> transfers = new ArrayList<>();
        for (MemoryEvidence evidence : evidences) {
            String type = evidence.getEvidenceType();
            if (EvidenceType.HIT.name().equals(type)) {
                hits.add(evidence);
                continue;
            }
            if (EvidenceType.TRANSFER_SUCCESS.name().equals(type)) {
                transfers.add(evidence);
            }
            if ((EvidenceType.TRANSFER_SUCCESS.name().equals(type)
                    || EvidenceType.PROMPTED_CORRECT.name().equals(type))
                    && isFreeChatEvidence(evidence, sessionAnalysisIds)) {
                freeChatPositives.add(evidence);
            }
        }

        double pScore = computePracticeScore(attempts);
        double iScore = computeIndependentScore(attempts, freeChatPositives);
        double tScore = computeTransferScore(hits, transfers, now);

        double rawMastery = properties.getMasteryWeightPractice() * pScore
                + properties.getMasteryWeightIndependent() * iScore
                + properties.getMasteryWeightTransfer() * tScore;
        double clampedMastery = Math.max(0.0, Math.min(100.0, rawMastery));
        BigDecimal masteryLevel = BigDecimal.valueOf(clampedMastery).setScale(2, RoundingMode.HALF_UP);

        int consecutiveCorrect = computeConsecutiveCorrect(attempts, hits, freeChatPositives);
        int practicedCount = Math.max(attempts.size(), sessions.size());
        int attemptCorrectCount = (int) attempts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsCorrect()))
                .count();
        int correctCount = attemptCorrectCount + freeChatPositives.size();

        boolean qualified = isQualifiedForMastery(
                memory, attempts, sessions, freeChatPositives, transfers, correctCount, consecutiveCorrect);

        memory.setMasteryLevel(masteryLevel);
        memory.setConsecutiveCorrect(consecutiveCorrect);
        memory.setPracticedCount(practicedCount);
        memory.setCorrectCount(correctCount);

        lifecycleService.onMasteryEvaluated(memory, qualified, now);
        memoryMapper.updateMetricsAndStatus(memory);
        return memory;
    }

    private static boolean isFreeChatEvidence(MemoryEvidence evidence, Set<String> sessionAnalysisIds) {
        if (StringUtils.hasText(evidence.getDedupKey()) && evidence.getDedupKey().startsWith("ATTEMPT:")) {
            return false;
        }
        return !StringUtils.hasText(evidence.getAnalysisId())
                || !sessionAnalysisIds.contains(evidence.getAnalysisId().trim());
    }

    private static double computePracticeScore(List<PracticeAttempt> attempts) {
        if (attempts.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (PracticeAttempt attempt : attempts) {
            if (!Boolean.TRUE.equals(attempt.getIsCorrect())) {
                continue;
            }
            sum += attempt.getScore() != null ? attempt.getScore().doubleValue() : 100.0;
        }
        return sum / attempts.size();
    }

    private static double computeIndependentScore(
            List<PracticeAttempt> attempts,
            List<MemoryEvidence> freeChatPositives) {
        int total = attempts.size() + freeChatPositives.size();
        if (total == 0) {
            return 0.0;
        }
        int independentCount = 0;
        for (PracticeAttempt attempt : attempts) {
            if (Boolean.TRUE.equals(attempt.getIsCorrect())
                    && Boolean.TRUE.equals(attempt.getIndependent())
                    && !Boolean.TRUE.equals(attempt.getHintUsed())) {
                independentCount++;
            }
        }
        for (MemoryEvidence evidence : freeChatPositives) {
            if (EvidenceType.TRANSFER_SUCCESS.name().equals(evidence.getEvidenceType())) {
                independentCount++;
            }
        }
        return 100.0 * independentCount / total;
    }

    private double computeTransferScore(
            List<MemoryEvidence> hits,
            List<MemoryEvidence> transfers,
            LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(Math.max(1, properties.getRecentWindowDays()));
        long recentTransfers = transfers.stream()
                .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().isBefore(cutoff))
                .count();
        long recentHits = hits.stream()
                .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().isBefore(cutoff))
                .count();
        long t = (recentTransfers + recentHits > 0) ? recentTransfers : transfers.size();
        long h = (recentTransfers + recentHits > 0) ? recentHits : hits.size();
        if (t == 0) {
            return 0.0;
        }
        return 100.0 * t / (t + h);
    }

    private static int computeConsecutiveCorrect(
            List<PracticeAttempt> attempts,
            List<MemoryEvidence> hits,
            List<MemoryEvidence> freeChatPositives) {
        List<OutcomeEvent> timeline = new ArrayList<>();
        for (PracticeAttempt attempt : attempts) {
            timeline.add(new OutcomeEvent(
                    attempt.getCreatedAt(),
                    attempt.getId() == null ? 0L : attempt.getId(),
                    Boolean.TRUE.equals(attempt.getIsCorrect())));
        }
        for (MemoryEvidence hit : hits) {
            timeline.add(new OutcomeEvent(
                    hit.getCreatedAt(),
                    hit.getId() == null ? 0L : hit.getId(),
                    false));
        }
        for (MemoryEvidence positive : freeChatPositives) {
            timeline.add(new OutcomeEvent(
                    positive.getCreatedAt(),
                    positive.getId() == null ? 0L : positive.getId(),
                    true));
        }
        timeline.sort(Comparator.comparing(OutcomeEvent::time, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OutcomeEvent::id, Comparator.reverseOrder()));

        int streak = 0;
        for (OutcomeEvent event : timeline) {
            if (event.positive()) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private boolean isQualifiedForMastery(
            WeaknessMemory memory,
            List<PracticeAttempt> attempts,
            List<PracticeSession> sessions,
            List<MemoryEvidence> freeChatPositives,
            List<MemoryEvidence> transfers,
            int correctCount,
            int consecutiveCorrect) {
        int totalPractices = attempts.size() + freeChatPositives.size();
        if (totalPractices < Math.max(1, properties.getMasteryMinPractices())) {
            return false;
        }
        long distinctAttemptSessions = attempts.stream()
                .map(PracticeAttempt::getSessionId)
                .distinct()
                .count();
        long distinctSessions = Math.max(distinctAttemptSessions,
                sessions.stream()
                        .filter(s -> s.getTotalAttempts() != null && s.getTotalAttempts() > 0)
                        .map(PracticeSession::getId)
                        .distinct()
                        .count());
        if (distinctSessions < Math.max(1, properties.getMasteryMinSessions())) {
            return false;
        }
        if (consecutiveCorrect < Math.max(1, properties.getMasteryMinConsecutiveCorrect())) {
            return false;
        }
        double accuracy = (double) correctCount / totalPractices;
        if (accuracy < properties.getMasteryMinAccuracy()) {
            return false;
        }
        Set<Long> independentSessions = new HashSet<>();
        int independentCorrect = 0;
        for (PracticeAttempt attempt : attempts) {
            if (Boolean.TRUE.equals(attempt.getIsCorrect())
                    && Boolean.TRUE.equals(attempt.getIndependent())
                    && !Boolean.TRUE.equals(attempt.getHintUsed())) {
                independentCorrect++;
                if (attempt.getSessionId() != null) {
                    independentSessions.add(attempt.getSessionId());
                }
            }
        }
        long independentFreeChat = freeChatPositives.stream()
                .filter(e -> EvidenceType.TRANSFER_SUCCESS.name().equals(e.getEvidenceType()))
                .count();
        if (independentCorrect + independentFreeChat < 1) {
            return false;
        }
        if (MemoryType.GRAMMAR.name().equals(memory.getMemoryType())) {
            return !transfers.isEmpty() || independentSessions.size() >= 2;
        }
        return true;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return CollectionUtils.isEmpty(list) ? List.of() : list;
    }

    private record OutcomeEvent(LocalDateTime time, long id, boolean positive) {
    }
}
