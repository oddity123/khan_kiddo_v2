package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.model.UserWeaknessMemory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 薄弱点选点优先分：复发分 × 时间衰减 × 带练冷却惩罚。
 */
public final class DailyPracticeRankingSupport {

    /** 冷却中显著减分系数（仅当活跃条数 &gt; 1 时生效）。 */
    public static final double COOLDOWN_PENALTY = 0.05;

    private DailyPracticeRankingSupport() {
    }

    public static double priorityScore(
            UserWeaknessMemory memory,
            LocalDateTime now,
            int decayHalfLifeDays,
            boolean coolingDown,
            int activeCount) {
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(now, "now");
        int hits = memory.getHitCount() == null || memory.getHitCount() < 1 ? 1 : memory.getHitCount();
        double recurrence = 1.0 + Math.log(hits);
        double decay = timeDecay(memory.getLastSeenAt(), now, decayHalfLifeDays);
        double score = recurrence * decay;
        if (coolingDown && activeCount > 1) {
            score *= COOLDOWN_PENALTY;
        }
        return score;
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
