package com.khankiddo.learning.growth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class GrowthCardMintListenerTest {

    private static final long USER_ID = 7L;
    private static final String ANALYSIS_ID = "analysis-1";

    @Mock
    private GrowthCardMintGateway gateway;

    private GrowthCardMintListener listener;

    @BeforeEach
    void setUp() {
        listener = new GrowthCardMintListener(gateway);
    }

    @Test
    void onMintRequested_finishesMintingBeforeReturning() {
        AtomicBoolean minted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Thread.sleep(40);
            minted.set(true);
            return null;
        }).when(gateway).mintAfterAnalysis(USER_ID, ANALYSIS_ID);

        listener.onMintRequested(new GrowthCardMintRequestedEvent(USER_ID, ANALYSIS_ID));

        assertThat(minted)
                .as("词汇卡必须在分析完成事件发出前落库，否则详情页卡片库为空")
                .isTrue();
    }
}
