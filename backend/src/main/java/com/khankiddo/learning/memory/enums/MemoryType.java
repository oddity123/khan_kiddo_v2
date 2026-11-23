package com.khankiddo.learning.memory.enums;

import com.khankiddo.learning.knowledge.PointChannel;

/**
 * 薄弱点记忆性质分类（与字典 PointChannel 对应，不承担细粒度分类体系职责）。
 */
public enum MemoryType {
    GRAMMAR,
    HABIT,
    LEXICAL;

    public static MemoryType fromChannel(PointChannel channel) {
        if (channel == null) {
            return GRAMMAR;
        }
        return switch (channel) {
            case FLUENCY -> HABIT;
            case LEXICAL -> LEXICAL;
            case RULE, CHINESE -> GRAMMAR;
        };
    }
}
