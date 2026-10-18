package com.khankiddo.learning.exception;

import com.khankiddo.learning.voice.VoicePracticeMessages;

/**
 * 任一人均或全站额度已用完。页面只展示 {@link VoicePracticeMessages#QUOTA_EXHAUSTED}。
 */
public class VoicePracticeQuotaExceededException extends RuntimeException {

    public VoicePracticeQuotaExceededException() {
        super(VoicePracticeMessages.QUOTA_EXHAUSTED);
    }
}
