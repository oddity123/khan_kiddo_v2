package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 口语薄弱点长期记忆、练习闭环、掌握度及优先级排程参数（配置单源）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.weakness-memory")
public class WeaknessMemoryProperties {

    /** 关闭后不触发分析完成后的记忆命中记录与评估。 */
    private boolean enabled = true;

    /** 是否在分析完成后触发单次 LLM 回检评估（Practice Assessment / Transfer）。 */
    private boolean assessmentEnabled = true;

    /** NEW → ACTIVE 所需近期窗口内独立命中次数。 */
    private int confirmMinHits = 2;

    /** 近期命中与迁移率统计窗口（天）。 */
    private int recentWindowDays = 30;

    /** 达到 MASTERED 的最低有效练习次数。 */
    private int masteryMinPractices = 5;

    /** 达到 MASTERED 的最低独立练习 Session 数。 */
    private int masteryMinSessions = 2;

    /** 达到 MASTERED 的最低连续正确次数。 */
    private int masteryMinConsecutiveCorrect = 3;

    /** 达到 MASTERED 的最低练习正确率（0~1）。 */
    private double masteryMinAccuracy = 0.85;

    /** 掌握度公式权重 M = wP * P + wI * I + wT * T。 */
    private double masteryWeightPractice = 0.4;
    private double masteryWeightIndependent = 0.3;
    private double masteryWeightTransfer = 0.3;

    /** 优先级公式权重 Priority = wR * R + wS * S + wM * (100 - M) + wF * F。 */
    private double priorityWeightRecentFreq = 0.35;
    private double priorityWeightSeverity = 0.25;
    private double priorityWeightMasteryGap = 0.25;
    private double priorityWeightForgetting = 0.15;

    /** 流利度（HABIT / FLUENCY_*）锚点在优先分上的乘数（<1 压低流利度霸榜）。 */
    private double fluencyRecurrenceScale = 0.55;

    /** 冷却中显著减分系数（仅当候选池 > 1 时生效）。 */
    private double cooldownPenalty = 0.05;

    /** 带练后冷却所需新分析场次数。 */
    private int cooldownAnalyses = 2;

    /** 带练冷却最短小时数。 */
    private int cooldownHours = 24;

    /** 每次推荐薄弱点上限（受产品上限 3 约束；实时语音减负默认聚焦 1 项）。 */
    private int recommendGoalLimit = 1;

    /** 每次推荐到期表达卡上限。 */
    private int recommendVocabLimit = 2;

    /** 各生命周期默认下次复习间隔（天）。 */
    private int reviewIntervalActiveDays = 1;
    private int reviewIntervalPracticingDays = 2;
    private int reviewIntervalMasteredDays = 14;

    /** 单次分析后回检评估最多覆盖的目标薄弱点数。 */
    private int assessmentMaxTargets = 3;

    /** 送入回检 LLM 的对话最大字符数。 */
    private int assessmentMaxTranscriptChars = 8000;

    /** 每日练习「薄弱点（Weak points）」推荐中排除的 pointId（如一次性词汇缺口走 Extra vocabulary 成长卡）。 */
    private List<String> excludeRecommendPointIds = new ArrayList<>(List.of("LEXICAL_GAP"));
}
