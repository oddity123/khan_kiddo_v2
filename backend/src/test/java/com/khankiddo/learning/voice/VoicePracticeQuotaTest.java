package com.khankiddo.learning.voice;

import com.khankiddo.learning.config.VoicePracticeCapProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoicePracticeQuotaTest {

    @Test
    void defaultsMatchTheWorkingCaps() {
        VoicePracticeCapProperties caps = new VoicePracticeCapProperties();
        assertThat(caps.userDailyLimitSeconds()).isEqualTo(8 * 60);
        assertThat(caps.userMonthlyLimitSeconds()).isEqualTo(30 * 60);
        assertThat(caps.siteDailyLimitSeconds()).isEqualTo(40 * 60);
        assertThat(caps.siteMonthlyLimitSeconds()).isEqualTo(600 * 60);
    }

    @Test
    void remainingIsTheTightestCap() {
        VoicePracticeCapProperties caps = new VoicePracticeCapProperties();
        assertThat(VoicePracticeQuota.remainingSeconds(caps, usage(60, 0, 0, 0)))
                .isEqualTo(8 * 60 - 60);
        assertThat(VoicePracticeQuota.remainingSeconds(caps, usage(8 * 60, 0, 0, 0))).isZero();
        assertThat(VoicePracticeQuota.remainingSeconds(caps, usage(0, 30 * 60, 0, 0))).isZero();
        assertThat(VoicePracticeQuota.remainingSeconds(caps, usage(0, 0, 40 * 60, 0))).isZero();
        assertThat(VoicePracticeQuota.remainingSeconds(
                caps, usage(0, 0, 0, caps.siteMonthlyLimitSeconds()))).isZero();
    }

    private static VoicePracticeQuota.Usage usage(int userDay, int userMonth, int siteDay, int siteMonth) {
        return new VoicePracticeQuota.Usage(userDay, userMonth, siteDay, siteMonth);
    }
}
