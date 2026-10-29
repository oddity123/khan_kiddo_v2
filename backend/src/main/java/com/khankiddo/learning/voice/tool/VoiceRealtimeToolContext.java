package com.khankiddo.learning.voice.tool;

/**
 * 工具执行时的会话上下文（身份与练习场次由已鉴权 WS 注入，不信任模型入参）。
 */
public record VoiceRealtimeToolContext(long userId, long practiceSessionId) {
}
