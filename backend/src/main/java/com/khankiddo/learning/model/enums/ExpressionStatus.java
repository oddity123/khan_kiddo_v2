package com.khankiddo.learning.model.enums;

/** 表达事实状态，与知识点的教育严重度 ErrorLevel 分开。 */
public enum ExpressionStatus {
    ERROR, RISK, UNNATURAL, OPTIONAL;

    /** 旧模型输出、旧保存请求与历史行保留原来的问题统计口径。 */
    public static ExpressionStatus effective(ExpressionStatus status) {
        return status == null ? ERROR : status;
    }

    public static boolean isIssue(ExpressionStatus status) {
        return effective(status) != OPTIONAL;
    }
}
