package com.khankiddo.learning.conversation;

import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 对话分析接口按用户限流（内存滑动窗口，单实例有效）。
 */
@Component
@RequiredArgsConstructor
public class ConversationAnalyzeRateLimiter {

    private final ConversationAnalysisProperties properties;
    private final ConcurrentHashMap<Long, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    public void checkAllowed(Long userId) {
        if (!properties.isAnalyzeRateLimitEnabled() || userId == null) {
            return;
        }

        int maxRequests = properties.getAnalyzeRateLimitMaxRequests();
        long windowMs = properties.getAnalyzeRateLimitWindowMinutes() * 60_000L;
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(userId, ignored -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                throw new TooManyRequestsException("对话分析请求过于频繁，请稍后再试");
            }
            timestamps.addLast(now);
        }
    }
}
