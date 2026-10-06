package com.khankiddo.learning.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextSupportTest {

    @Test
    void trimToNull_blankBecomesNull() {
        assertNull(TextSupport.trimToNull(null));
        assertNull(TextSupport.trimToNull("  "));
        assertEquals("hi", TextSupport.trimToNull("  hi  "));
    }

    @Test
    void trimTrailingSlashes_stripsAllAndFallsBack() {
        assertEquals("http://a:1", TextSupport.trimTrailingSlashes(" http://a:1// ", "x"));
        assertEquals("http://a:1", TextSupport.trimTrailingSlashes("http://a:1", "x"));
        assertEquals("x", TextSupport.trimTrailingSlashes("  ", "x"));
        assertEquals("x", TextSupport.trimTrailingSlashes(null, "x"));
    }
}
