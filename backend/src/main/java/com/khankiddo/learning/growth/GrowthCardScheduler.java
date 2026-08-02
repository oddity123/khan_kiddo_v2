package com.khankiddo.learning.growth;

import java.time.LocalDate;

public final class GrowthCardScheduler {

    public record ReviewResult(String status, LocalDate nextDueAt) {
    }

    public ReviewResult apply(String grade, LocalDate today) {
        return switch (grade) {
            case "again" -> new ReviewResult("unfamiliar", today.plusDays(1));
            case "fuzzy" -> new ReviewResult("fuzzy", today.plusDays(3));
            case "good" -> new ReviewResult("mastered", null);
            default -> throw new IllegalArgumentException("unknown grade: " + grade);
        };
    }
}
