package com.khankiddo.learning.memory.event;

/**
 * 对话分析持久化提交后事件：触发确定性 HIT 沉淀与练习回检评估。
 */
public record MemoryAnalysisCompletedEvent(Long userId, String analysisId, String sourceConversationId) {
}
