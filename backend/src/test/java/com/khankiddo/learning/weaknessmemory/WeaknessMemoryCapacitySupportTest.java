package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.UserWeaknessMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryCapacitySupportTest {

    @Mock
    private UserWeaknessMemoryMapper memoryMapper;

    private WeaknessMemoryProperties properties;
    private WeaknessMemoryCapacitySupport support;

    @BeforeEach
    void setUp() {
        properties = new WeaknessMemoryProperties();
        properties.setActivePoolLimit(12);
        support = new WeaknessMemoryCapacitySupport(memoryMapper, properties);
    }

    @Test
    void skipsWhenWithinLimit() {
        when(memoryMapper.countActiveByUserId(1L)).thenReturn(12L);

        support.enforceActivePoolLimit(1L);

        verify(memoryMapper, never()).findActiveForEviction(eq(1L), eq(1));
        verify(memoryMapper, never()).archiveById(org.mockito.ArgumentMatchers.anyLong(), eq(1L));
    }

    @Test
    void archivesOverflowVictims() {
        when(memoryMapper.countActiveByUserId(1L)).thenReturn(14L);
        when(memoryMapper.findActiveForEviction(1L, 2)).thenReturn(List.of(
                UserWeaknessMemory.builder().id(101L).userId(1L).build(),
                UserWeaknessMemory.builder().id(102L).userId(1L).build()));

        support.enforceActivePoolLimit(1L);

        verify(memoryMapper).archiveById(101L, 1L);
        verify(memoryMapper).archiveById(102L, 1L);
    }
}
