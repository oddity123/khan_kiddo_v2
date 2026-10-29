package com.khankiddo.learning.voice.tool;

/**
 * 单个豆包实时语音工具。
 */
public interface VoiceRealtimeToolHandler {

    String name();

    /**
     * @return 回传给模型的 JSON 文本（非外层 conversation.item 信封），及可选面板事件
     */
    VoiceRealtimeToolResult execute(VoiceRealtimeToolContext context, String argumentsJson);
}
