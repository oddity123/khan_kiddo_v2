package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教育总结根结构，与库表 {@code educational_summary} JSON 及分析流水线输出格式一致。
 *
 * <pre>{@code { "report": { "overallStats": {...}, "overallSummary": {...} } }}</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationalSummaryDto {

    /** 诊断报告内容 */
    private EducationalSummaryReportDto report;
}
