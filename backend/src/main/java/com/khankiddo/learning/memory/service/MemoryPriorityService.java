package com.khankiddo.learning.memory.service;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.knowledge.PointDefinition;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 练习优先级排程：
 * Priority = 0.35 * R(近期频率) + 0.25 * S(严重程度) + 0.25 * (100 - M) + 0.15 * F(遗忘风险)
 */
@Service
@RequiredArgsConstructor
public class MemoryPriorityService {

    private static final int MAX_RECOMMEND_TARGETS = 3;
    private static final String FLUENCY_PREFIX = "FLUENCY_";

    private final WeaknessMemoryProperties properties;
    private final WeaknessMemoryMapper memoryMapper;
    private final ConversationAnalysisMapper analysisMapper;
    private final PointDictionary pointDictionary;

    public List<WeaknessMemory> selectDailyTargets(
            Long userId,
            Set<Long> excludeMemoryIds,
            int limit,
            LocalDateTime now) {
        if (userId == null) {
            return List.of();
        }
        List<WeaknessMemory> candidates = memoryMapper.findByUserId(
                userId,
                List.of(
                        MemoryStatus.ACTIVE.name(),
                        MemoryStatus.PRACTICING.name(),
                        MemoryStatus.MASTERED.name()));
        if (CollectionUtils.isEmpty(candidates)) {
            return List.of();
        }

        Set<String> excludedPointIds = toUpperTrimmedSet(properties.getExcludeRecommendPointIds());
        List<WeaknessMemory> eligible = new ArrayList<>();
        for (WeaknessMemory memory : candidates) {
            if (isMasteredAndNotDue(memory, now)) {
                continue;
            }
            if (StringUtils.hasText(memory.getPointId())
                    && excludedPointIds.contains(memory.getPointId().trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (memory.getId() != null && excludeMemoryIds != null && excludeMemoryIds.contains(memory.getId())) {
                continue;
            }
            eligible.add(memory);
        }
        if (eligible.isEmpty()) {
            return List.of();
        }

        int cappedLimit = Math.min(MAX_RECOMMEND_TARGETS, Math.max(1, limit));
        boolean poolHasNonFluency = eligible.stream().anyMatch(m -> !isFluencyHabit(m));
        int poolSize = eligible.size();

        List<ScoredTarget> scored = new ArrayList<>(poolSize);
        for (WeaknessMemory memory : eligible) {
            boolean cooling = isCoolingDown(memory, now);
            double score = computePriorityScore(memory, now, cooling, poolSize);
            scored.add(new ScoredTarget(memory, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredTarget::score).reversed()
                .thenComparing(s -> s.memory().getId(), Comparator.nullsLast(Long::compareTo)));

        List<WeaknessMemory> selected = new ArrayList<>(cappedLimit);
        int fluencySelected = 0;
        for (ScoredTarget item : scored) {
            if (selected.size() >= cappedLimit) {
                break;
            }
            boolean fluency = isFluencyHabit(item.memory());
            if (fluency && poolHasNonFluency && fluencySelected >= 1) {
                continue;
            }
            selected.add(item.memory());
            if (fluency) {
                fluencySelected++;
            }
        }
        return selected;
    }

    public double computePriorityScore(
            WeaknessMemory memory,
            LocalDateTime now,
            boolean coolingDown,
            int candidatePoolSize) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(now, "now");

        double r = recentFrequencyScore(memory);
        double s = severityScore(memory.getPointId());
        double mastery = memory.getMasteryLevel() == null ? 0.0 : memory.getMasteryLevel().doubleValue();
        double masteryGap = Math.max(0.0, Math.min(100.0, 100.0 - mastery));
        double f = forgettingRiskScore(memory, now);

        double score = properties.getPriorityWeightRecentFreq() * r
                + properties.getPriorityWeightSeverity() * s
                + properties.getPriorityWeightMasteryGap() * masteryGap
                + properties.getPriorityWeightForgetting() * f;

        if (coolingDown && candidatePoolSize > 1) {
            score *= Math.max(0.0, properties.getCooldownPenalty());
        }
        if (isFluencyHabit(memory)) {
            score *= Math.max(0.0, properties.getFluencyRecurrenceScale());
        }
        return Math.round(score * 100.0) / 100.0;
    }

    public boolean isCoolingDown(WeaknessMemory memory, LocalDateTime now) {
        if (memory == null || memory.getLastPracticedAt() == null) {
            return false;
        }
        long hours = Duration.between(memory.getLastPracticedAt(), now).toHours();
        if (hours < Math.max(0, properties.getCooldownHours())) {
            return true;
        }
        long analysesSince = analysisMapper.countByUserIdAndStatusCreatedAfter(
                memory.getUserId(), "success", memory.getLastPracticedAt());
        return analysesSince < Math.max(0, properties.getCooldownAnalyses());
    }

    public static boolean isFluencyHabit(WeaknessMemory memory) {
        if (memory == null) {
            return false;
        }
        if (MemoryType.HABIT.name().equalsIgnoreCase(memory.getMemoryType())) {
            return true;
        }
        return StringUtils.hasText(memory.getPointId())
                && memory.getPointId().trim().regionMatches(true, 0, FLUENCY_PREFIX, 0, FLUENCY_PREFIX.length());
    }

    public static String buildSummary(int goalCount, String firstTitle, int vocabCount) {
        if (goalCount <= 0 && vocabCount <= 0) {
            return "暂无专属练习，可先随便聊聊今天";
        }
        StringBuilder sb = new StringBuilder("练：");
        if (goalCount > 0 && StringUtils.hasText(firstTitle)) {
            sb.append(firstTitle.trim());
        } else if (goalCount > 0) {
            sb.append(goalCount).append(" 个薄弱点");
        }
        if (vocabCount > 0) {
            if (goalCount > 0) {
                sb.append(" · ");
            }
            sb.append(vocabCount).append(" 个表达");
        }
        return sb.toString();
    }

    private static boolean isMasteredAndNotDue(WeaknessMemory memory, LocalDateTime now) {
        if (!MemoryStatus.MASTERED.name().equals(memory.getStatus())) {
            return false;
        }
        return memory.getNextReviewAt() == null || memory.getNextReviewAt().isAfter(now);
    }

    private static double recentFrequencyScore(WeaknessMemory memory) {
        int recentHits = memory.getRecentHitCount() == null ? 0 : memory.getRecentHitCount();
        if (recentHits > 0) {
            return Math.min(100.0, recentHits * 20.0);
        }
        int totalHits = memory.getHitCount() == null ? 0 : memory.getHitCount();
        return Math.min(40.0, totalHits * 10.0);
    }

    private double severityScore(String pointId) {
        if (!StringUtils.hasText(pointId)) {
            return 60.0;
        }
        PointDefinition point = pointDictionary.pointsById().get(pointId.trim());
        if (point == null || !StringUtils.hasText(point.errorLevel())) {
            return 60.0;
        }
        return switch (point.errorLevel().trim().toUpperCase()) {
            case "FATAL" -> 100.0;
            case "BASIC" -> 70.0;
            case "NATURAL" -> 45.0;
            case "STYLE" -> 25.0;
            default -> 60.0;
        };
    }

    private static double forgettingRiskScore(WeaknessMemory memory, LocalDateTime now) {
        if (memory.getLastPracticedAt() == null) {
            return 80.0;
        }
        if (memory.getNextReviewAt() != null && !now.isBefore(memory.getNextReviewAt())) {
            long overdueDays = Math.max(0L, Duration.between(memory.getNextReviewAt(), now).toDays());
            return Math.min(100.0, 70.0 + overdueDays * 5.0);
        }
        long daysSincePractice = Math.max(0L, Duration.between(memory.getLastPracticedAt(), now).toDays());
        return Math.min(100.0, daysSincePractice * 5.0);
    }

    private static Set<String> toUpperTrimmedSet(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                set.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return set;
    }

    private record ScoredTarget(WeaknessMemory memory, double score) {
    }
}
