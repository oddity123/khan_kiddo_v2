package com.khankiddo.learning.voice;

/**
 * 额度打满时给页面的固定说明。任一帽（人/天、人/月、全站/天、全站/月）都用这一句。
 */
public final class VoicePracticeMessages {

    public static final String QUOTA_EXHAUSTED = "本月练习额度已用完";

    public static final String QUOTA_EVENT_JSON =
            "{\"type\":\"proxy.quota_exhausted\",\"message\":\"" + QUOTA_EXHAUSTED + "\"}";

    private VoicePracticeMessages() {
    }
}
