package com.khankiddo.learning.growth;

import java.time.LocalDate;

/**
 * 轻量 Anki 式固定间隔（不做完整 SM-2）：
 * <ul>
 *   <li>again → unfamiliar，+1 天</li>
 *   <li>hard（兼容旧值 fuzzy）→ fuzzy，+2 天</li>
 *   <li>good → fuzzy，+4 天（继续进入队列）</li>
 *   <li>easy → mastered，退出队列</li>
 * </ul>
 */
public final class GrowthCardScheduler {

    public record ReviewResult(String status, LocalDate nextDueAt) {
    }

    public ReviewResult apply(String grade, LocalDate today) {
        String normalized = normalize(grade);
        return switch (normalized) {
            case "again" -> new ReviewResult("unfamiliar", today.plusDays(1));
            case "hard" -> new ReviewResult("fuzzy", today.plusDays(2));
            case "good" -> new ReviewResult("fuzzy", today.plusDays(4));
            case "easy" -> new ReviewResult("mastered", null);
            default -> throw new IllegalArgumentException("unknown grade: " + grade);
        };
    }

    private static String normalize(String grade) {
        if (grade == null) {
            return "";
        }
        String value = grade.trim().toLowerCase();
        if ("fuzzy".equals(value)) {
            return "hard";
        }
        return value;
    }
}
