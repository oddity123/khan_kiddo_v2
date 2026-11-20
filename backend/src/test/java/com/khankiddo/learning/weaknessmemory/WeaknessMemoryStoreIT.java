package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.UserWeaknessMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class WeaknessMemoryStoreIT {

    @Autowired
    private WeaknessMemoryStore store;
    @Autowired
    private UserWeaknessMemoryMapper memoryMapper;
    @Autowired
    private WeaknessMemoryProperties properties;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM user_weakness_memory");
        properties.setActivePoolLimit(12);
    }

    @Test
    void createsMergesEvictsAndCleansBySource() {
        store.applyOperations(1L, "a-1", List.of(createOp("spend + 时间后漏 -ing", "FAM_NONFINITE", "NF_GERUND_OBJECT")));

        List<UserWeaknessMemory> afterCreate = memoryMapper.findActiveByUserId(1L);
        assertEquals(1, afterCreate.size());
        Long memoryId = afterCreate.get(0).getId();
        assertEquals(1, afterCreate.get(0).getHitCount());

        store.applyOperations(1L, "a-2", List.of(mergeOp(
                memoryId, "spend + 时间后漏 -ing", "FAM_NONFINITE", "NF_GERUND_OBJECT")));

        UserWeaknessMemory merged = memoryMapper.findByIdAndUserId(memoryId, 1L).orElseThrow();
        assertEquals(2, merged.getHitCount());
        assertEquals("a-2", merged.getSourceAnalysisId());

        properties.setActivePoolLimit(1);
        store.applyOperations(1L, "a-3", List.of(createOp("whether 前误加 the", "FAM_ARTICLE", "ART_DEFINITE")));
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

    private static WeaknessMemoryOperation createOp(String title, String habit, String point) {
        return new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE, null, title, habit, point);
    }

    private static WeaknessMemoryOperation mergeOp(Long id, String title, String habit, String point) {
        return new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_MERGE, id, title, habit, point);
    }
}
