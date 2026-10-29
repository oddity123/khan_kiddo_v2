package com.khankiddo.learning.voice.tool;

import org.springframework.stereotype.Component;

/**
 * FC 尖峰假工具：验证后端分发与回传。后续学习工具旁路新增 Handler，不扩本类。
 */
@Component
public class PingPracticeLabToolHandler implements VoiceRealtimeToolHandler {

    public static final String NAME = "ping_practice_lab";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public VoiceRealtimeToolResult execute(VoiceRealtimeToolContext context, String argumentsJson) {
        return VoiceRealtimeToolResult.of(
                "{\"ok\":true,\"tool\":\"" + NAME + "\",\"note\":\"spike function call succeeded\","
                        + "\"practiceSessionId\":" + context.practiceSessionId() + "}");
    }
}
