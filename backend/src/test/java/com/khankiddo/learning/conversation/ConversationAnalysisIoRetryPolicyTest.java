package com.khankiddo.learning.conversation;

import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisIoRetryPolicyTest {

    @Test
    void retriesPrematureCloseAndTimeouts() {
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new RuntimeException("Connection prematurely closed DURING response"))).isTrue();
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new SocketTimeoutException("Read timed out"))).isTrue();
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new ResourceAccessException("I/O error on GET", new ClosedChannelException()))).isTrue();
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new RuntimeException("Connection reset by peer"))).isTrue();
    }

    @Test
    void doesNotRetryAuthOrClientErrors() {
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new RuntimeException("401 Unauthorized invalid_api_key"))).isFalse();
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new RuntimeException("403 Forbidden"))).isFalse();
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new RuntimeException("content_filter triggered"))).isFalse();
        assertThat(ConversationAnalysisIoRetryPolicy.isRetryable(
                new BadRequestException("AI 分析结果格式无效，请重试"))).isFalse();
    }
}
