package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.UserWeaknessMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class WeaknessMemoryStoreIT {

    @Autowired
    private WeaknessMemoryStore store;
    @Autowired
    private UserWeaknessMemoryMapper memoryMapper;
    @Autowired
    private ConversationAnalysisMapper analysisMapper;
    @Autowired
    private ConversationAnalysisItemMapper itemMapper;
    @Autowired
    private WeaknessMemoryExampleSupport exampleSupport;
    @Autowired
    private WeaknessMemoryProperties properties;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM user_weakness_memory");
        jdbcTemplate.update("DELETE FROM conversation_analysis_item");
        jdbcTemplate.update("DELETE FROM conversation_analysis");
        properties.setActivePoolLimit(12);
    }

    @Test
    void createsMergesRejectsForgedEvictsAndCleansBySource() {
        seedAnalysis(1L, "a-1", "I spend 2 hours watch TV");
        seedAnalysis(1L, "a-2", "I spend 3 hours play game");

        assertTrue(exampleSupport.isAuthenticBadExample(1L, "I spend 2 hours watch TV"));
        assertFalse(exampleSupport.isAuthenticBadExample(2L, "I spend 2 hours watch TV"));

        store.applyOperations(1L, "a-1", List.of(createOp(
                "spend + 时间后漏 -ing",
                "I spend 2 hours watch TV",
                "I spend 2 hours watching TV")));

        List<UserWeaknessMemory> afterCreate = memoryMapper.findActiveByUserId(1L);
        assertEquals(1, afterCreate.size());
        Long memoryId = afterCreate.get(0).getId();
        assertEquals(1, afterCreate.get(0).getHitCount());

        store.applyOperations(1L, "a-2", List.of(mergeOp(
                memoryId,
                "spend + 时间后漏 -ing",
                "I spend 3 hours play game",
                "I spend 3 hours playing games")));

        UserWeaknessMemory merged = memoryMapper.findByIdAndUserId(memoryId, 1L).orElseThrow();
        assertEquals(2, merged.getHitCount());
        assertEquals("a-2", merged.getSourceAnalysisId());

        store.applyOperations(1L, "a-1", List.of(createOp(
                "forged",
                "this sentence was never said",
                "rewrite")));
        assertEquals(1, memoryMapper.countActiveByUserId(1L));

        properties.setActivePoolLimit(1);
        seedAnalysis(1L, "a-3", "Whether the weather is good");
        store.applyOperations(1L, "a-3", List.of(createOp(
                "whether 前误加 the",
                "Whether the weather is good",
                "Whether weather is good")));
        // hit_count 更低的新建项被淘汰归档，保留已归并的高 hit 项
        assertEquals(1, memoryMapper.countActiveByUserId(1L));
        assertEquals(UserWeaknessMemory.STATUS_ACTIVE,
                memoryMapper.findByIdAndUserId(memoryId, 1L).orElseThrow().getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_weakness_memory WHERE user_id = 1 AND status = 'ARCHIVED'",
                Integer.class));

        int deleted = store.deleteBySourceAnalysis(1L, "a-3");
        assertEquals(1, deleted);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_weakness_memory WHERE user_id = 1 AND source_analysis_id = 'a-3'",
                Integer.class));
    }

    private void seedAnalysis(Long userId, String analysisId, String original) {
        analysisMapper.insert(ConversationAnalysis.builder()
                .userId(userId)
                .analysisId(analysisId)
                .conversationContent("User: " + original)
                .status("success")
                .processingTimeMs(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        itemMapper.batchInsert(List.of(ConversationAnalysisItem.builder()
                .analysisId(analysisId)
                .sentenceId(1L)
                .originalSentence(original)
                .pointId("NF_GERUND_OBJECT")
                .errorPoint("gerund")
                .suggestion(original + " (fixed)")
                .build()));
    }

    private static WeaknessMemoryOperation createOp(String title, String bad, String rewrite) {
        return new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE,
                null,
                title,
                "diagnosis",
                "coaching",
                "FAM_NONFINITE",
                "NF_GERUND_OBJECT",
                bad,
                rewrite);
    }

    private static WeaknessMemoryOperation mergeOp(Long id, String title, String bad, String rewrite) {
        return new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_MERGE,
                id,
                title,
                "diagnosis",
                "coaching",
                "FAM_NONFINITE",
                "NF_GERUND_OBJECT",
                bad,
                rewrite);
    }
}
