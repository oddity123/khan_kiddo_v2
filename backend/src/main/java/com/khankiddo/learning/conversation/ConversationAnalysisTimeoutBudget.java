package com.khankiddo.learning.conversation;

import com.khankiddo.learning.config.ConversationAnalysisProperties;

import java.time.Duration;

/**
 * 对话分析超时预算。公式：
 * 分批 Stage2 墙钟 ≈ 并发波次 × 非流式单次超时 × 失败批次重试次数；
 * 另加 Stage1 / 中文 Review / Stage3 各一次 chat 超时。
 * 必须明显小于 SSE 10 分钟。
 */
public final class ConversationAnalysisTimeoutBudget {

    public static final Duration SSE_TIMEOUT = Duration.ofMinutes(10);
    static final int FAILED_BATCH_MAX_ATTEMPTS = 2;
    private static final int OTHER_STAGE_COUNT = 3;

    private ConversationAnalysisTimeoutBudget() {
    }

    public static Duration worstCaseAnalysis(ConversationAnalysisProperties properties, int englishSentenceCount) {
        Duration chat = properties.getChatTimeout();
        return worstCaseStage2(properties, englishSentenceCount)
                .plus(chat.multipliedBy(OTHER_STAGE_COUNT));
    }

    public static Duration worstCaseStage2(ConversationAnalysisProperties properties, int englishSentenceCount) {
        Duration chat = properties.getChatTimeout();
        if (englishSentenceCount > properties.getBatchThreshold()) {
            int batches = ceilDiv(englishSentenceCount, properties.getBatchSize());
            int waves = ceilDiv(batches, properties.getBatchConcurrentLimit());
            return chat.multipliedBy((long) waves * FAILED_BATCH_MAX_ATTEMPTS);
        }
        return properties.getStreamWallClockTimeout().plus(chat);
    }

    private static int ceilDiv(int value, int divisor) {
        if (value <= 0 || divisor <= 0) {
            return 0;
        }
        return (value + divisor - 1) / divisor;
    }
}
