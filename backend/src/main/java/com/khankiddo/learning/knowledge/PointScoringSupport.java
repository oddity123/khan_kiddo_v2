package com.khankiddo.learning.knowledge;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 从 {@link PointDefinition} 解析评分 profile 与严重度，供口语评分与习惯卡使用。
 * 分类真源为 {@code pointId}；{@code scoreProfile} 用于扣分权重与维度归属。
 */
public final class PointScoringSupport {

    private PointScoringSupport() {
    }

    public static String scoreProfile(PointDefinition point) {
        if (point == null || !StringUtils.hasText(point.scoreProfile())) {
            return "UNKNOWN";
        }
        return point.scoreProfile().trim().toUpperCase(Locale.ROOT);
    }

    public static String errorLevel(PointDefinition point) {
        if (point == null || !StringUtils.hasText(point.errorLevel())) {
            return "STYLE";
        }
        return point.errorLevel().trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isFatal(PointDefinition point) {
        return "FATAL".equals(errorLevel(point));
    }
}
