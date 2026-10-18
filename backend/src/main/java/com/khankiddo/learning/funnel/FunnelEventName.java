package com.khankiddo.learning.funnel;

import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 本月要记的漏斗事件。不接受名单以外的名字。
 */
public enum FunnelEventName {
    visit_analyze,
    click_sample,
    analyze_start,
    analyze_success,
    analyze_error,
    voice_start,
    voice_end,
    voice_saved,
    voice_to_analyze;

    public static FunnelEventName parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String name = raw.trim();
        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
