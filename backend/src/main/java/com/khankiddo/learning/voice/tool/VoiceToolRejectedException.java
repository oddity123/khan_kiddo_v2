package com.khankiddo.learning.voice.tool;

/**
 * 工具入参 / 范围校验失败；{@code code} 回传模型（如 {@code card_not_in_session}）。
 */
public class VoiceToolRejectedException extends RuntimeException {

    private final String code;

    public VoiceToolRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
