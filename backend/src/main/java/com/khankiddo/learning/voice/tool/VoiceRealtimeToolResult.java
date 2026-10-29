package com.khankiddo.learning.voice.tool;

import com.khankiddo.learning.voice.VoiceLearningEvent;

/**
 * 工具执行结果：回传模型的 JSON，外加可选的面板事件（仅成功落库后才带）。
 */
public record VoiceRealtimeToolResult(String outputJson, VoiceLearningEvent learningEvent) {

    public static VoiceRealtimeToolResult of(String outputJson) {
        return new VoiceRealtimeToolResult(outputJson, null);
    }

    public static VoiceRealtimeToolResult withEvent(String outputJson, VoiceLearningEvent event) {
        return new VoiceRealtimeToolResult(outputJson, event);
    }
}
