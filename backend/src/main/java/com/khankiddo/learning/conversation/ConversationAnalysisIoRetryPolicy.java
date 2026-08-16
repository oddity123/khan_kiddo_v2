package com.khankiddo.learning.conversation;

import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 判断 LLM 调用失败是否属于可重试的临时 I/O。
 * 鉴权、4xx 业务错误和内容审核不重试。
 */
public final class ConversationAnalysisIoRetryPolicy {

    private ConversationAnalysisIoRetryPolicy() {
    }

    public static boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }
        if (isNonRetryable(error)) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (isRetryableType(current) || isRetryableMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isNonRetryable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                String lower = message.toLowerCase();
                if (lower.contains("invalid_api_key")
                        || lower.contains("unauthorized")
                        || lower.contains("content_filter")
                        || lower.contains("审核")
                        || lower.contains("401")
                        || lower.contains("403")
                        || lower.contains("400 ")
                        || lower.contains("status 400")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isRetryableType(Throwable error) {
        if (error instanceof ResourceAccessException
                || error instanceof SocketTimeoutException
                || error instanceof TimeoutException
                || error instanceof IOException) {
            return true;
        }
        String className = error.getClass().getName();
        return className.contains("PrematureCloseException")
                || className.contains("ReadTimeoutException")
                || className.contains("HttpTimeoutException");
    }

    private static boolean isRetryableMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("prematurely closed")
                || lower.contains("connection reset")
                || lower.contains("broken pipe")
                || lower.contains("read timed out")
                || lower.contains("goaway")
                || lower.contains("503")
                || lower.contains("502")
                || lower.contains("504")
                || lower.contains("429");
    }
}
