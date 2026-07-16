package com.khankiddo.learning.rag.grammar;

import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarRagQuerySupportTest {

    @Test
    void query_shouldPutUserIdInChatMemoryId() {
        Query query = GrammarRagQuerySupport.query(42L, "时态错误");

        assertEquals("时态错误", query.text());
        assertEquals(42L, GrammarRagQuerySupport.requireUserIdFromQuery(query));
        assertNull(GrammarRagQuerySupport.maxResultsOverride(query));
    }

    @Test
    void query_shouldCarryMaxResultsOverride() {
        Query query = GrammarRagQuerySupport.query(1L, "冠词", 3);

        assertEquals(3, GrammarRagQuerySupport.maxResultsOverride(query));
    }

    @Test
    void query_shouldRejectBlankText() {
        assertThrows(IllegalArgumentException.class,
                () -> GrammarRagQuerySupport.query(1L, "  "));
    }

    @Test
    void requireUserIdFromQuery_shouldFailWithoutMetadata() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GrammarRagQuerySupport.requireUserIdFromQuery(Query.from("hi")));
        assertTrue(ex.getMessage().contains("chatMemoryId"));
    }
}
