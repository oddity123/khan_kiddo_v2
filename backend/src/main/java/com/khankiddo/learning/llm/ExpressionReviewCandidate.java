package com.khankiddo.learning.llm;

/**
 * Phrase Review 输入侧的英文 unnatural 候选（已由 Java 侧 NATURAL 过滤）。
 */
public record ExpressionReviewCandidate(
        Long sentenceId,
        String originalSentence,
        String suggestion,
        String point,
        String pointId) {
}
