package com.khankiddo.learning.weaknessmemory;

/**
 * LLM 归并决策中的单条操作（Java 侧校验后再落库）。
 */
public record WeaknessMemoryOperation(
        String op,
        Long memoryId,
        String title,
        String diagnosis,
        String coaching,
        String anchorHabitKey,
        String anchorPointId,
        String badExample,
        String targetRewrite
) {
    public static final String OP_CREATE = "CREATE";
    public static final String OP_MERGE = "MERGE";
    public static final String OP_SKIP = "SKIP";
}
