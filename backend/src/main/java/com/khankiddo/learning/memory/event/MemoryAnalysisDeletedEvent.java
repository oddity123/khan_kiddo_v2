package com.khankiddo.learning.memory.event;

/**
 * 分析记录删除后事件：清理派生证据/会话并重算受影响薄弱点记忆。
 */
public record MemoryAnalysisDeletedEvent(Long userId, String analysisId) {
}
