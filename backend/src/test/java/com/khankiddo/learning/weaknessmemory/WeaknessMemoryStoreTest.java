package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.UserWeaknessMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryStoreTest {

    @Mock
    private UserWeaknessMemoryMapper memoryMapper;
    @Mock
    private WeaknessMemoryCapacitySupport capacitySupport;

    private WeaknessMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new WeaknessMemoryStore(memoryMapper, capacitySupport);
    }

    @Test
    void createPersistsTitleAndAnchorsOnly() {
        store.applyOperations(1L, "a-1", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE,
                null,
                "spend + 时间后漏 -ing",
                "FAM_NONFINITE",
                "NF_GERUND_OBJECT")));

        ArgumentCaptor<UserWeaknessMemory> captor = ArgumentCaptor.forClass(UserWeaknessMemory.class);
        verify(memoryMapper).insert(captor.capture());
        assertEquals("spend + 时间后漏 -ing", captor.getValue().getTitle());
        assertEquals("FAM_NONFINITE", captor.getValue().getAnchorHabitKey());
        assertEquals(1, captor.getValue().getHitCount());
        assertEquals("a-1", captor.getValue().getSourceAnalysisId());
        verify(capacitySupport).enforceActivePoolLimit(1L);
    }

    @Test
    void createRejectsBlankTitle() {
        store.applyOperations(1L, "a-1", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE, null, "  ", null, null)));

        verify(memoryMapper, never()).insert(any());
        verify(capacitySupport).enforceActivePoolLimit(1L);
    }

    @Test
    void createRejectsCompoundTitle() {
        store.applyOperations(1L, "a-1", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE,
                null,
                "feel后误用-ing；be动词位置误用名词",
                "FAM_WORD_FORM",
                "WORD_FORM_POS")));

        verify(memoryMapper, never()).insert(any());
        verify(capacitySupport).enforceActivePoolLimit(1L);
    }

    @Test
    void mergeIncrementsHitCountForOwnedMemory() {
        when(memoryMapper.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(
                UserWeaknessMemory.builder()
                        .id(9L)
                        .userId(1L)
                        .hitCount(2)
                        .status(UserWeaknessMemory.STATUS_ACTIVE)
                        .build()));

        store.applyOperations(1L, "a-2", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_MERGE,
                9L,
                "spend + 时间后漏 -ing",
                "FAM_NONFINITE",
                "NF_GERUND_OBJECT")));

        ArgumentCaptor<UserWeaknessMemory> captor = ArgumentCaptor.forClass(UserWeaknessMemory.class);
        verify(memoryMapper).updateMerged(captor.capture());
        assertEquals(3, captor.getValue().getHitCount());
        assertEquals("a-2", captor.getValue().getSourceAnalysisId());
        assertNull(captor.getValue().getLastPracticedAt());
    }

    @Test
    void mergeRejectsOtherUsersMemory() {
        when(memoryMapper.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        store.applyOperations(1L, "a-2", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_MERGE,
                9L,
                "title",
                null,
                null)));

        verify(memoryMapper, never()).updateMerged(any());
    }

    @Test
    void deleteBySourceAnalysisDelegates() {
        when(memoryMapper.deleteByUserIdAndSourceAnalysisId(1L, "a-1")).thenReturn(2);

        assertEquals(2, store.deleteBySourceAnalysis(1L, "a-1"));
        verify(memoryMapper).deleteByUserIdAndSourceAnalysisId(eq(1L), eq("a-1"));
    }
}
