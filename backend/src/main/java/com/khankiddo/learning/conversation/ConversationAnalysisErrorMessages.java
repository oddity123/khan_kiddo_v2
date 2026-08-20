package com.khankiddo.learning.conversation;

import com.khankiddo.learning.exception.BadRequestException;
import org.springframework.dao.DataAccessException;
import org.springframework.util.StringUtils;

/**
 * 对话分析 SSE / 失败落库对外文案。内部异常细节只留在服务端日志。
 */
public final class ConversationAnalysisErrorMessages {

    public static final String ANALYZE_FAILED = "分析失败，请稍后重试";
    public static final String PERSIST_FAILED = "保存分析结果失败，请重试";

    private ConversationAnalysisErrorMessages() {
    }

    public static String toUserMessage(Throwable error) {
        if (isPersistFailure(error)) {
            return PERSIST_FAILED;
        }
        if (error instanceof BadRequestException && isSafeUserMessage(error.getMessage())) {
            return error.getMessage().trim();
        }
        return ANALYZE_FAILED;
    }

    /**
     * 失败记录落库前再过滤一遍，避免历史调用方把 JDBC 原文写入 error_message。
     */
    public static String sanitizeStoredMessage(String errorMessage) {
        if (isSafeUserMessage(errorMessage)) {
            return ConversationAnalysisPersistSupport.truncate(
                    errorMessage.trim(), ConversationAnalysisPersistSupport.ERROR_MESSAGE_MAX);
        }
        return ANALYZE_FAILED;
    }

    private static boolean isPersistFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataAccessException || current instanceof java.sql.SQLException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isSafeUserMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String value = message.trim();
        if (value.length() > 200) {
            return false;
        }
        String lower = value.toLowerCase();
        return !lower.contains("error updating database")
                && !lower.contains("insert into")
                && !lower.contains("sql:")
                && !lower.contains("jdbc")
                && !lower.contains("mapper/")
                && !lower.contains(".java:")
                && !lower.contains("exception:")
                && !lower.contains("caused by")
                && !lower.contains("communications link")
                && !lower.contains("data truncation")
                && !lower.contains("/users/")
                && !lower.contains("\\users\\");
    }
}
