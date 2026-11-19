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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryStoreTest {

    @Mock
    private UserWeaknessMemoryMapper memoryMapper;
    @Mock
    private WeaknessMemoryExampleSupport exampleSupport;
    @Mock
    private WeaknessMemoryCapacitySupport capacitySupport;

    private WeaknessMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new WeaknessMemoryStore(memoryMapper, exampleSupport, capacitySupport);
    }

    @Test
    void createPersistsWhenExampleAuthentic() {
        when(exampleSupport.isAuthenticBadExample(1L, "I spend 2 hours watch TV")).thenReturn(true);

        store.applyOperations(1L, "a-1", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE,
                null,
                "spend + 时间后漏 -ing",
                "把 spend time 当成 spend to",
                "开口前自检 -ing",
                "FAM_NONFINITE",
                "NF_GERUND_OBJECT",
                "I spend 2 hours watch TV",
                "I spend 2 hours watching TV")));

        ArgumentCaptor<UserWeaknessMemory> captor = ArgumentCaptor.forClass(UserWeaknessMemory.class);
        verify(memoryMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getHitCount());
        assertEquals("a-1", captor.getValue().getSourceAnalysisId());
        verify(capacitySupport).enforceActivePoolLimit(1L);
    }

    @Test
    void createRejectsForgedExample() {
        when(exampleSupport.isAuthenticBadExample(1L, "made up")).thenReturn(false);

        store.applyOperations(1L, "a-1", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_CREATE,
                null,
                "title",
                "diagnosis",
                "coaching",
                null,
                null,
                "made up",
                "rewrite")));

        verify(memoryMapper, never()).insert(any());
        verify(capacitySupport).enforceActivePoolLimit(1L);
    }

    @Test
    void mergeIncrementsHitCountForOwnedMemory() {
        when(exampleSupport.isAuthenticBadExample(1L, "I spend 2 hours watch TV")).thenReturn(true);
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
                "diagnosis",
                "coaching",
                "FAM_NONFINITE",
                "NF_GERUND_OBJECT",
                "I spend 2 hours watch TV",
                "I spend 2 hours watching TV")));

        ArgumentCaptor<UserWeaknessMemory> captor = ArgumentCaptor.forClass(UserWeaknessMemory.class);
        verify(memoryMapper).updateMerged(captor.capture());
        assertEquals(3, captor.getValue().getHitCount());
        assertEquals("a-2", captor.getValue().getSourceAnalysisId());
    }

    @Test
    void mergeRejectsOtherUsersMemory() {
        when(exampleSupport.isAuthenticBadExample(anyLong(), anyString())).thenReturn(true);
        when(memoryMapper.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        store.applyOperations(1L, "a-2", List.of(new WeaknessMemoryOperation(
                WeaknessMemoryOperation.OP_MERGE,
                9L,
                "title",
                "diagnosis",
                "coaching",
                null,
                null,
                "I spend 2 hours watch TV",
                "rewrite")));

        verify(memoryMapper, never()).updateMerged(any());
    }

    @Test
    void deleteBySourceAnalysisDelegates() {
        when(memoryMapper.deleteByUserIdAndSourceAnalysisId(1L, "a-1")).thenReturn(2);

        assertEquals(2, store.deleteBySourceAnalysis(1L, "a-1"));
        verify(memoryMapper).deleteByUserIdAndSourceAnalysisId(eq(1L), eq("a-1"));
    }
}
