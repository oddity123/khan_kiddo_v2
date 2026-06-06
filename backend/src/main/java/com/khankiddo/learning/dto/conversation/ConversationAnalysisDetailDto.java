package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话分析详情 API 响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAnalysisDetailDto {

    /**
     * 分析记录 ID
     */
    private String analysisId;

    /** 原始对话字幕/文本 */
    private String conversationContent;

    /** 分析状态，如 {@code success}、{@code failed} */
    private String status;

    /** 失败时的错误说明 */
    private String errorMessage;

    /** 分析耗时（毫秒） */
    private Long processingTimeMs;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    private String llmModelId;
    private String llmModelName;
    private String llmProvider;

    /**
     * 教育诊断概要（统计、综合得分、AI 文字总结）
     */
    private EducationalSummaryDto educationalSummary;

    /** 按句聚合的错误与建议列表 */
    private List<AnalysisItemDto> items;

    /** 错误类型分布，用于饼图等展示 */
    private List<ErrorTypeDistributionDto> errorTypeDistribution;
}
