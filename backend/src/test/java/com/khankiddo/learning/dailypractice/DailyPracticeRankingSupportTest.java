package com.khankiddo.learning.dailypractice;

import com.khankiddo.learning.model.UserWeaknessMemory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyPracticeRankingSupportTest {

    @Test
    void higherHitCountRanksHigherWhenSameSeenTime() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 12, 0);
        UserWeaknessMemory low = base(1, now.minusDays(1), null);
        UserWeaknessMemory high = base(5, now.minusDays(1), null);

        double lowScore = DailyPracticeRankingSupport.priorityScore(low, now, 21, false, 2);
        double highScore = DailyPracticeRankingSupport.priorityScore(high, now, 21, false, 2);
        assertTrue(highScore > lowScore);
    }

    @Test
    void oldLastSeenDecaysScore() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 12, 0);
        UserWeaknessMemory fresh = base(2, now.minusDays(1), null);
        UserWeaknessMemory stale = base(2, now.minusDays(42), null);

        double freshScore = DailyPracticeRankingSupport.priorityScore(fresh, now, 21, false, 2);
        double staleScore = DailyPracticeRankingSupport.priorityScore(stale, now, 21, false, 2);
        assertTrue(freshScore > staleScore);
    }

    @Test
    void cooldownPenaltyAppliesOnlyWhenMultipleActive() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 12, 0);
        UserWeaknessMemory memory = base(3, now.minusDays(1), now.minusHours(1));

        double multi = DailyPracticeRankingSupport.priorityScore(memory, now, 21, true, 2);
        double single = DailyPracticeRankingSupport.priorityScore(memory, now, 21, true, 1);
        assertTrue(multi < single);
        assertEquals(single * DailyPracticeRankingSupport.COOLDOWN_PENALTY, multi, 1e-9);
    }

    @Test
    void coolingDownWhenHoursOrAnalysesInsufficient() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 12, 0);
        UserWeaknessMemory memory = base(1, now, now.minusHours(2));

        assertTrue(DailyPracticeRankingSupport.isCoolingDown(memory, now, 24, 2, 10));
        assertTrue(DailyPracticeRankingSupport.isCoolingDown(memory, now, 1, 2, 0));
        assertFalse(DailyPracticeRankingSupport.isCoolingDown(memory, now, 1, 2, 2));
        assertFalse(DailyPracticeRankingSupport.isCoolingDown(
                base(1, now, null), now, 24, 2, 0));
    }

    @Test
    void summaryFormatsGoalsAndVocab() {
        assertEquals("练：spend 漏 -ing · 2 个表达",
                DailyPracticeRankingSupport.buildSummary(1, "spend 漏 -ing", 2));
        assertEquals("暂无专属练习，可先随便聊聊今天",
                DailyPracticeRankingSupport.buildSummary(0, null, 0));
    }

    private static UserWeaknessMemory base(int hits, LocalDateTime lastSeen, LocalDateTime practiced) {
        return UserWeaknessMemory.builder()
                .id(1L)
                .hitCount(hits)
                .lastSeenAt(lastSeen)
                .lastPracticedAt(practiced)
                .status(UserWeaknessMemory.STATUS_ACTIVE)
                .build();
    }
}
