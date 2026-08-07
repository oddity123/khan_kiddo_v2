package com.khankiddo.learning.exception;

/**
 * 游客免费分析次数已用尽。
 */
public class GuestQuotaExceededException extends RuntimeException {

    public GuestQuotaExceededException(String message) {
        super(message);
    }
}
