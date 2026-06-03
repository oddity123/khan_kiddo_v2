package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.dto.conversation.PerformanceDimensionScoresDto;

/**
 * 评分结果：综合分 + 各子维度分（均为确定性整数，范围见配置 min/max）。
 */
public record PerformanceScoreResult(
        int overall,
        int naturalness,
        int accuracy,
        int fluency,
        int lexical) {

    public PerformanceDimensionScoresDto toDimensionScoresDto() {
        return PerformanceDimensionScoresDto.builder()
                .naturalness(naturalness)
                .accuracy(accuracy)
                .fluency(fluency)
                .lexical(lexical)
                .build();
    }
}
