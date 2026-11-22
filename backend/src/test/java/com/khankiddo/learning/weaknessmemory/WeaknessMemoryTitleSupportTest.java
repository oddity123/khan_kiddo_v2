package com.khankiddo.learning.weaknessmemory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeaknessMemoryTitleSupportTest {

    @Test
    void acceptsConcreteMicroHabitTitle() {
        assertEquals(
                "spend + 时间后漏 -ing",
                WeaknessMemoryTitleSupport.sanitizeOrNull("  spend + 时间后漏 -ing  "));
    }

    @Test
    void rejectsCompoundTitleWithSemicolon() {
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull(
                "feel后误用-ing；be动词位置误用名词"));
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull(
                "feel后误用-ing; be动词位置误用名词"));
    }

    @Test
    void rejectsCoachingPhrases() {
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull("开口前先确认时态"));
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull("说完不要回头重复"));
    }

    @Test
    void rejectsTooLongTitle() {
        String longTitle = "描".repeat(WeaknessMemoryTitleSupport.MAX_TITLE_LENGTH + 1);
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull(longTitle));
    }

    @Test
    void rejectsBlank() {
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull(null));
        assertNull(WeaknessMemoryTitleSupport.sanitizeOrNull("   "));
    }
}
