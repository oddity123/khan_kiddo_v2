package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryExampleSupportTest {

    @Mock
    private UserWeaknessMemoryMapper memoryMapper;

    private WeaknessMemoryExampleSupport support;

    @BeforeEach
    void setUp() {
        support = new WeaknessMemoryExampleSupport(memoryMapper);
    }

    @Test
    void rejectsBlankOrNullExample() {
        assertFalse(support.isAuthenticBadExample(1L, null));
        assertFalse(support.isAuthenticBadExample(1L, "   "));
        assertFalse(support.isAuthenticBadExample(null, "I spend 2 hours watch TV"));
    }

    @Test
    void trimsBeforeLookup() {
        when(memoryMapper.existsOriginalSentenceByUserId(1L, "I spend 2 hours watch TV"))
                .thenReturn(true);

        assertTrue(support.isAuthenticBadExample(1L, "  I spend 2 hours watch TV  "));
        verify(memoryMapper).existsOriginalSentenceByUserId(1L, "I spend 2 hours watch TV");
    }
}
