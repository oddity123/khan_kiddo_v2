package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话概要统计指标，与前端 {@code EducationalSummaryStats} 字段一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationalSummaryStatsDto {

    /** 本次分析检出的优化点（错误条数）总数 */
    private Integer totalIssues;

    /** 参与统计的用户发言句数 */
    private Integer totalSentences;

    /** 含中文表达的用户句数（不计入语法错误） */
    private Integer chineseExpressionCount;

    /** 主要挑战：出现频次最高的错误类型中文名（2–12 字短语） */
    private String mainCategory;

    /**
     * 综合口语自然度得分（60–98），由配置化权重算法确定性计算，非 LLM 打分。
     */
    private Integer performanceScore;

    /** 各子维度得分，便于后续展示分项雷达图等 */
    private PerformanceDimensionScoresDto dimensionScores;
}
