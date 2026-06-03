package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 口语表现评分子维度得分（60–98，由 {@link com.khankiddo.learning.conversation.scoring.PerformanceScorer} 计算）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceDimensionScoresDto {

    /** 表达自然度：搭配、中式英语、生硬表达等 */
    private Integer naturalness;

    /** 语法准确度：时态、一致、句式结构等 */
    private Integer accuracy;

    /** 文本流畅度：未完成句、中文夹杂、冗余等 */
    private Integer fluency;

    /** 词汇表达：用词、搭配、词汇量等 */
    private Integer lexical;
}
