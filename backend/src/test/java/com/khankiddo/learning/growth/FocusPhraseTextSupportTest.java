package com.khankiddo.learning.growth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusPhraseTextSupportTest {

    @Test
    void normalizeKey_stripsPunctuationAndLowercases() {
        assertEquals("look forward to see", FocusPhraseTextSupport.normalizeKey("Look forward to see!"));
        assertEquals("", FocusPhraseTextSupport.normalizeKey("   "));
    }

    @Test
    void hasSubstantiveDiff_ignoresCaseAndPunctuation() {
        assertFalse(FocusPhraseTextSupport.hasSubstantiveDiff("Happy!", "happy"));
        assertTrue(FocusPhraseTextSupport.hasSubstantiveDiff("paper", "document"));
        assertFalse(FocusPhraseTextSupport.hasSubstantiveDiff(null, "x"));
    }
}
