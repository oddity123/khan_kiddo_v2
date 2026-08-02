package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;

import java.util.List;

/**
 * {@link HabitCardScorer} 的输入：本场所有已判点错误命中 + 中文表达（用于合成
 * {@code CHINESE_CODE_SWITCH} 命中）。
 */
public record HabitScoreInput(
        List<ErrorHit> errorHits,
        List<ChineseExpressionDto> chineseExpressions
) {

    /**
     * 单条错误命中；{@code errorLevel} 可空，为空时打分器查字典兜底。
     */
    public record ErrorHit(
            String pointId,
            String sentenceId,
            String originalSentence,
            String errorPoint,
            String suggestion,
            String errorLevel
    ) {
    }
}
