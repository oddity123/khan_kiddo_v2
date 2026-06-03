package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口语自然度评分配置，权重与维度定义见 {@code classpath:scoring/performance-scoring.yml}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.performance-scoring")
public class PerformanceScoringProperties {

    private double baseScore = 98;
    private int minScore = 60;
    private int maxScore = 98;
    private double densityDecay = 0.22;
    private double densityMultiplier = 34;
    private double maxWeightedPenaltyPerSentence = 6.0;
    private double severeSentencePenalty = 4.0;
    private double naturalnessSentencePenalty = 5.0;
    private double severeWeightThreshold = 2.8;
    private double defaultTypeWeight = 1.0;

    /** ProblemType 枚举名 → 扣分权重 */
    private Map<String, Double> typeWeights = new LinkedHashMap<>();

    /** 子维度：naturalness / accuracy / fluency / lexical */
    private Map<String, DimensionConfig> dimensions = new LinkedHashMap<>();

    @Data
    public static class DimensionConfig {
        private double weightInOverall = 0.25;
        private List<String> types = List.of();
    }
}
