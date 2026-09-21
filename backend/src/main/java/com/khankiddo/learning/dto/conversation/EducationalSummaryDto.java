package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /** 含中文的用户句（表达缺口，不计入语法错误） */
    private List<ChineseExpressionDto> chineseExpressions;

    /**
     * NATURAL 英文句经 Phrase Review 预计算的短表达对立（对称 {@link #chineseExpressions}）。
     */
    private List<ExpressionPhraseDto> expressionPhrases;

    /** Stage 3 生成的 Top 行动卡本场诊断文案。 */
    private List<ActionCardDiagnosisDto> actionCardDiagnoses;
}
