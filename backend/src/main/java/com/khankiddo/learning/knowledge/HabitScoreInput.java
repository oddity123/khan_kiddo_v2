package com.khankiddo.learning.knowledge;

import java.util.List;

/**
 * {@link HabitCardScorer} 的输入：本场所有已判点错误命中（仅 Stage2 语法通道）。
 * 中文表达走独立知识卡通道，不参与习惯竞争。
 */
public record HabitScoreInput(List<ErrorHit> errorHits) {

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
