package com.khankiddo.learning.growth;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrowthCardSchedulerTest {

    private final GrowthCardScheduler scheduler = new GrowthCardScheduler();
    private final LocalDate today = LocalDate.of(2026, 8, 2);

    @Test
    void again_shouldSetUnfamiliarAndDueTomorrow() {
        GrowthCardScheduler.ReviewResult result = scheduler.apply("again", today);

        assertEquals("unfamiliar", result.status());
        assertEquals(today.plusDays(1), result.nextDueAt());
    }

    @Test
    void hard_shouldSetFuzzyAndDueInTwoDays() {
        GrowthCardScheduler.ReviewResult result = scheduler.apply("hard", today);

        assertEquals("fuzzy", result.status());
        assertEquals(today.plusDays(2), result.nextDueAt());
    }

    @Test
    void fuzzyAlias_shouldBehaveLikeHard() {
        GrowthCardScheduler.ReviewResult result = scheduler.apply("fuzzy", today);

        assertEquals("fuzzy", result.status());
        assertEquals(today.plusDays(2), result.nextDueAt());
    }

    @Test
    void good_shouldSetFuzzyAndDueInFourDays() {
        GrowthCardScheduler.ReviewResult result = scheduler.apply("good", today);

        assertEquals("fuzzy", result.status());
        assertEquals(today.plusDays(4), result.nextDueAt());
    }

    @Test
    void easy_shouldSetMasteredWithNoDueDate() {
        GrowthCardScheduler.ReviewResult result = scheduler.apply("easy", today);

        assertEquals("mastered", result.status());
        assertNull(result.nextDueAt());
    }

    @Test
    void unknownGrade_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scheduler.apply("unknown", today));

        assertEquals("unknown grade: unknown", ex.getMessage());
    }
}
