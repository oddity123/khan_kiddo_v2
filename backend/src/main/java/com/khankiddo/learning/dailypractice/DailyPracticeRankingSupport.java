package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.model.UserWeaknessMemory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 薄弱点选点优先分：复发分 × 时间衰减 × 带练冷却惩罚 ×（可选）流利度降权。
 */
public final class DailyPracticeRankingSupport {

    /** 冷却中显著减分系数（仅当活跃条数 &gt; 1 时生效）。 */
    public static final double COOLDOWN_PENALTY = 0.05;

    private static final String FLUENCY_PREFIX = "FLUENCY_";

    private DailyPracticeRankingSupport() {
    }

    public static double priorityScore(
            UserWeaknessMemory memory,
            LocalDateTime now,
            int decayHalfLifeDays,
            boolean coolingDown,
            int activeCount) {
        return priorityScore(memory, now, decayHalfLifeDays, coolingDown, activeCount, 1.0);
    }

    public static double priorityScore(
            UserWeaknessMemory memory,
            LocalDateTime now,
            int decayHalfLifeDays,
            boolean coolingDown,
            int activeCount,
            double fluencyRecurrenceScale) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(now, "now");
        int hits = memory.getHitCount() == null || memory.getHitCount() < 1 ? 1 : memory.getHitCount();
        double recurrence = 1.0 + Math.log(hits);
        double decay = timeDecay(memory.getLastSeenAt(), now, decayHalfLifeDays);
        double score = recurrence * decay;
        if (coolingDown && activeCount > 1) {
            score *= COOLDOWN_PENALTY;
        }
        if (isFluencyAnchor(memory)) {
            double scale = fluencyRecurrenceScale;
            if (scale < 0.0) {
                scale = 0.0;
            }
            score *= scale;
        }
        return score;
    }

    public static boolean isFluencyAnchor(UserWeaknessMemory memory) {
        if (memory == null) {
            return false;
        }
        return startsWithFluency(memory.getAnchorHabitKey())
                || startsWithFluency(memory.getAnchorPointId());
    }

    private static boolean startsWithFluency(String value) {
        return StringUtils.hasText(value) && value.trim().regionMatches(true, 0, FLUENCY_PREFIX, 0, FLUENCY_PREFIX.length());
    }

    public static boolean isCoolingDown(
            UserWeaknessMemory memory,
            LocalDateTime now,
            int cooldownHours,
            int cooldownAnalyses,
            long analysesSincePractice) {
        if (memory == null || memory.getLastPracticedAt() == null) {
            return false;
        }
        long hours = Duration.between(memory.getLastPracticedAt(), now).toHours();
        if (hours < Math.max(0, cooldownHours)) {
            return true;
        }
        return analysesSincePractice < Math.max(0, cooldownAnalyses);
    }

    static double timeDecay(LocalDateTime lastSeenAt, LocalDateTime now, int halfLifeDays) {
        if (lastSeenAt == null || halfLifeDays <= 0) {
            return 1.0;
        }
        long days = Math.max(0, Duration.between(lastSeenAt, now).toDays());
        return Math.exp(-days * Math.log(2) / halfLifeDays);
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
}
