package com.khankiddo.learning.voice;

import com.khankiddo.learning.config.VoicePracticeCapProperties;

/**
 * 四顶帽取最紧的剩余秒数。调用方传入已发生的墙钟秒（含未结束场次冻结的额度）。
 */
public final class VoicePracticeQuota {

    private VoicePracticeQuota() {
    }

    public record Usage(int userDaySeconds, int userMonthSeconds, int siteDaySeconds, int siteMonthSeconds) {
    }

    public static int remainingSeconds(VoicePracticeCapProperties caps, Usage usage) {
        return Math.min(
                Math.min(
                        left(caps.userDailyLimitSeconds(), usage.userDaySeconds()),
                        left(caps.userMonthlyLimitSeconds(), usage.userMonthSeconds())),
                Math.min(
                        left(caps.siteDailyLimitSeconds(), usage.siteDaySeconds()),
                        left(caps.siteMonthlyLimitSeconds(), usage.siteMonthSeconds())));
    }

    private static int left(int limit, int used) {
        return Math.max(0, limit - Math.max(used, 0));
    }
}
