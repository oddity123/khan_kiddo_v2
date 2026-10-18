package com.khankiddo.learning.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 语音练习费用硬停。数字只定义在本类默认值与 {@code application.yml}（{@code app.voice.practice}）。
 * 分析 token 不计入 {@link #siteMonthlyYuan}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.voice.practice")
public class VoicePracticeCapProperties {

    /** 每人每天墙钟分钟 */
    private int userDailyMinutes = 8;

    /** 每人每月墙钟分钟 */
    private int userMonthlyMinutes = 30;

    /** 全站每天墙钟分钟 */
    private int siteDailyMinutes = 40;

    /** 全站每月金额硬停（元） */
    private BigDecimal siteMonthlyYuan = new BigDecimal("150");

    /** 墙钟分钟单价估算（元），用来把金额帽折成秒 */
    private BigDecimal yuanPerWallMinute = new BigDecimal("0.25");

    @PostConstruct
    void checkCaps() {
        if (userDailyMinutes < 0 || userMonthlyMinutes < 0 || siteDailyMinutes < 0 || siteMonthlyYuan.signum() < 0) {
            throw new IllegalStateException("app.voice.practice 时长或金额帽不能为负");
        }
        if (yuanPerWallMinute.signum() <= 0) {
            throw new IllegalStateException("app.voice.practice.yuan-per-wall-minute 必须大于 0");
        }
    }

    public int userDailyLimitSeconds() {
        return minutesToSeconds(userDailyMinutes);
    }

    public int userMonthlyLimitSeconds() {
        return minutesToSeconds(userMonthlyMinutes);
    }

    public int siteDailyLimitSeconds() {
        return minutesToSeconds(siteDailyMinutes);
    }

    public int siteMonthlyLimitSeconds() {
        BigDecimal minutes = siteMonthlyYuan
                .divide(yuanPerWallMinute, 4, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.HALF_UP);
        long seconds = minutes.longValue() * 60L;
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(seconds, 0);
    }

    private static int minutesToSeconds(int minutes) {
        long seconds = minutes * 60L;
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }
}
