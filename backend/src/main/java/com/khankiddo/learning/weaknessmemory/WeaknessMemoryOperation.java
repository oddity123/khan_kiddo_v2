package com.khankiddo.learning.weaknessmemory;

/**
 * LLM 归并决策：只产出宏观模式字段，不含句子或教学文案。
 */
public record WeaknessMemoryOperation(
        String op,
        Long memoryId,
        String title,
        String anchorHabitKey,
        String anchorPointId
) {
    public static final String OP_CREATE = "CREATE";
    public static final String OP_MERGE = "MERGE";
    public static final String OP_SKIP = "SKIP";
}
