package com.khankiddo.learning.memory.enums;

/**
 * 记忆证据类型：区分真实对话命中、专项练习错误、独立正向迁移、提示后正确。
 */
public enum EvidenceType {
    HIT,
    PRACTICE_ERROR,
    TRANSFER_SUCCESS,
    PROMPTED_CORRECT
}
