package com.khankiddo.learning.dto.conversation;

import com.khankiddo.learning.dto.growth.GrowthCardDto;
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

    /** 含中文的用户句（表达缺口，不计入语法错误） */
    private List<ChineseExpressionDto> chineseExpressions;

    /** 本次最该改的说话习惯（rank1），无足够证据时为 {@code null} */
    private ActionCardDto topHabit;

    /** 跨通道习惯行动卡 Top 1-3，无足够证据时为空列表 */
    private List<ActionCardDto> actionCards;

    /** 语法家族分布（饼图用），仅新数据（含 pointId）才计算，旧数据回退 {@link #errorTypeDistribution} */
    private List<FamilyDistributionDto> familyDistribution;

    /** 习惯成长卡铸卡状态：{@code pending}、{@code ready}、{@code failed}、{@code none} */
    private String habitGrowthMintStatus;

    /** 已铸成的习惯成长卡，{@code habitGrowthMintStatus=ready} 时有值 */
    private GrowthCardDto habitGrowthCard;

    /** 本场已生成的全部成长卡（habit + vocab），按 type / 时间排序 */
    private List<GrowthCardDto> growthCards;
}
