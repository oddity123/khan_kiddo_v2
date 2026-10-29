package com.khankiddo.learning.voice;

import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 教练对一次回合的判定。只有 {@link #INDEPENDENT_SUCCESS} 才可能触发保守进度上调。
 */
public enum VoicePracticeAttemptOutcome {
    INDEPENDENT_SUCCESS("independent_success"),
    PROMPTED("prompted"),
    UNCERTAIN("uncertain"),
    MISS("miss");

    private final String wire;

    VoicePracticeAttemptOutcome(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<VoicePracticeAttemptOutcome> fromWire(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String value = raw.trim();
        for (VoicePracticeAttemptOutcome outcome : values()) {
            if (outcome.wire.equals(value)) {
                return Optional.of(outcome);
            }
        }
        return Optional.empty();
    }
}
