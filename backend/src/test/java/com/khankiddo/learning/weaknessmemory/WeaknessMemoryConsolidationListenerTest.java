package com.khankiddo.learning.weaknessmemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeaknessMemoryConsolidationListenerTest {

    @Mock
    private WeaknessMemoryConsolidator consolidator;

    @Test
    void runsConsolidationOnVirtualThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(consolidator).consolidateAfterAnalysis(1L, "a-1");

        WeaknessMemoryConsolidationListener listener =
                new WeaknessMemoryConsolidationListener(consolidator);
        listener.onConsolidationRequested(new WeaknessMemoryConsolidationRequestedEvent(1L, "a-1"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(consolidator).consolidateAfterAnalysis(1L, "a-1");
    }
}
