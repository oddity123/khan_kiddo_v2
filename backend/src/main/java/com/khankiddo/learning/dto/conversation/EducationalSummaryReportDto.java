package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教育诊断报告主体，对应持久化 JSON 中 {@code report} 节点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationalSummaryReportDto {

    /** 量化统计与综合得分 */
    private EducationalSummaryStatsDto overallStats;

    /** AI 生成的定性总结 */
    private EducationalSummaryOverallDto overallSummary;
}
