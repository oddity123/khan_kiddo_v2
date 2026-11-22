package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.weakness-memory")
public class WeaknessMemoryProperties {

    /** 关闭后不触发分析完成后的薄弱点沉淀。 */
    private boolean enabled = true;

    /** 每人 ACTIVE 记忆上限；超限淘汰 hit_count 最低且 last_seen_at 最早的项。 */
    private int activePoolLimit = 12;

    /** 跨场预查历史天数。 */
    private int prefetchHistoryDays = 30;

    /** 每张 Top 卡预查同点历史错句上限。 */
    private int prefetchExampleLimit = 3;

    /** 每张 Top 卡向量相似错句上限（未启用 Qdrant 时跳过）。 */
    private int prefetchSimilarLimit = 3;

    /** 写入时每张行动卡带入 LLM 的代表错句上限。 */
    private int actionCardExampleLimit = 3;

    /** Step2 选点用：复发时间衰减半衰期（天）。 */
    private int decayHalfLifeDays = 21;

    /** Step2 选点用：带练后冷却所需新分析场次数。 */
    private int cooldownAnalyses = 2;

    /** Step2 选点用：带练冷却最短小时数。 */
    private int cooldownHours = 24;

    /**
     * Step2：FLUENCY_* 锚点在优先分上的乘数（&lt;1 压低流利度霸榜；不改 hit_count）。
     */
    private double fluencyRecurrenceScale = 0.55;

    /** Step2：每次推荐薄弱点上限。 */
    private int recommendGoalLimit = 2;

    /** Step2：每次推荐到期表达卡上限。 */
    private int recommendVocabLimit = 2;
}
