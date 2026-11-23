package com.khankiddo.learning.memory;

import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.memory.domain.MemoryEvidence;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.MemoryEvidenceMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
import com.khankiddo.learning.memory.service.MemoryService;
import com.khankiddo.learning.model.ConversationAnalysis;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MemoryServiceTest {

    @Autowired
    private MemoryService memoryService;
    @Autowired
    private WeaknessMemoryMapper memoryMapper;
    @Autowired
    private MemoryEvidenceMapper evidenceMapper;
    @Autowired
    private ConversationAnalysisMapper analysisMapper;
    @Autowired
    private ConversationAnalysisItemMapper itemMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM practice_attempt");
        jdbcTemplate.update("DELETE FROM practice_session");
        jdbcTemplate.update("DELETE FROM memory_evidence");
        jdbcTemplate.update("DELETE FROM weakness_memory");
        jdbcTemplate.update("DELETE FROM conversation_analysis_item");
        jdbcTemplate.update("DELETE FROM conversation_analysis");
    }

    @Test
    void recordsNewPromotesToActiveAndReconcilesOnDelete() {
        LocalDateTime t1 = LocalDateTime.now().minusDays(2);
        insertAnalysis(1L, "ana-1", t1, List.of(
                item("ana-1", 1L, "Yesterday I go home.", "PAST_SIMPLE_DONE", "go -> went")));

        List<Long> affected1 = memoryService.recordAnalysisHits(1L, "ana-1");
        assertEquals(1, affected1.size());

        WeaknessMemory afterFirst = memoryMapper.findByUserIdAndPointId(1L, "PAST_SIMPLE_DONE").orElseThrow();
        assertEquals(MemoryStatus.NEW.name(), afterFirst.getStatus());
        assertEquals(MemoryType.GRAMMAR.name(), afterFirst.getMemoryType());
        assertEquals("FAM_TENSE", afterFirst.getFamilyId());
        assertEquals(1, afterFirst.getHitCount());

        // 幂等重跑同一分析不重复计数
        memoryService.recordAnalysisHits(1L, "ana-1");
        assertEquals(1, memoryMapper.findByUserIdAndPointId(1L, "PAST_SIMPLE_DONE").orElseThrow().getHitCount());

        // 第二场命中，达到确认阈值 → ACTIVE
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);
        insertAnalysis(1L, "ana-2", t2, List.of(
                item("ana-2", 1L, "Last night I eat pizza.", "PAST_SIMPLE_DONE", "eat -> ate")));
        memoryService.recordAnalysisHits(1L, "ana-2");

        WeaknessMemory afterSecond = memoryMapper.findByUserIdAndPointId(1L, "PAST_SIMPLE_DONE").orElseThrow();
        assertEquals(MemoryStatus.ACTIVE.name(), afterSecond.getStatus());
        assertEquals(2, afterSecond.getHitCount());

        List<MemoryEvidence> evidences = evidenceMapper.findByMemoryId(afterSecond.getId(), null);
        assertEquals(2, evidences.size());

        // 删除 ana-2：保留记忆，回退为 hitCount=1 / NEW
        memoryService.handleAnalysisDeleted(1L, "ana-2");
        WeaknessMemory afterDeleteOne = memoryMapper.findByUserIdAndPointId(1L, "PAST_SIMPLE_DONE").orElseThrow();
        assertEquals(1, afterDeleteOne.getHitCount());
        assertEquals(MemoryStatus.NEW.name(), afterDeleteOne.getStatus());

        // 删除 ana-1：无残留证据与会话 → 清理孤立记忆
        memoryService.handleAnalysisDeleted(1L, "ana-1");
        assertTrue(memoryMapper.findByUserIdAndPointId(1L, "PAST_SIMPLE_DONE").isEmpty());
    }

    @Test
    void skipsChineseChannelItems() {
        insertAnalysis(2L, "ana-zh", LocalDateTime.now(), List.of(
                item("ana-zh", 1L, "那个...", "CHINESE_EXPRESSION", "that thing")));

        List<Long> affected = memoryService.recordAnalysisHits(2L, "ana-zh");
        assertTrue(affected.isEmpty());
        assertEquals(0L, memoryMapper.countAllByUserId(2L));
    }

    private void insertAnalysis(
            Long userId,
            String analysisId,
            LocalDateTime createdAt,
            List<ConversationAnalysisItem> items) {
        analysisMapper.insert(ConversationAnalysis.builder()
                .userId(userId)
                .analysisId(analysisId)
                .conversationContent("User: test")
                .status("success")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build());
        itemMapper.batchInsert(items);
    }

    private static ConversationAnalysisItem item(
            String analysisId,
            long sentenceId,
            String original,
            String pointId,
            String suggestion) {
        return ConversationAnalysisItem.builder()
                .analysisId(analysisId)
                .sentenceId(sentenceId)
                .originalSentence(original)
                .pointId(pointId)
                .errorPoint(pointId)
                .suggestion(suggestion)
                .build();
    }
}
