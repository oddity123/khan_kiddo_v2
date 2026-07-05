package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.config.PerformanceScoringProperties;
import com.khankiddo.learning.model.enums.ErrorLevel;
import com.khankiddo.learning.model.enums.ProblemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于配置权重的口语自然度评分：各子维度独立扣分 + 加权汇总综合分。
 *
 * <ul>
 *   <li>naturalness / accuracy / fluency / lexical 各自按维度错误类型独立计分；</li>
 *   <li>overall = Σ(维度分 × weight-in-overall)，可解释且与展示维度一致；</li>
 *   <li>「高严重度句子」与 {@link ErrorLevel#FATAL} 对齐；</li>
 *   <li>自然度句子占比修正仅作用于 naturalness 维度（overall 由其加权得出）；</li>
 *   <li>密度分母使用 totalSentences + densitySmoothingK 平滑短对话。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WeightedNaturalnessPerformanceScorer implements PerformanceScorer {

    private final PerformanceScoringProperties properties;

    @Override
    public PerformanceScoreResult score(PerformanceScoringInput input) {
        int totalSentences = Math.max(1, input.totalSentences());
        List<PerformanceScoringInput.SentenceErrors> sentences = input.sentencesWithErrors();

        if (CollectionUtils.isEmpty(sentences)) {
            int perfect = properties.getMaxScore();
            return new PerformanceScoreResult(perfect, perfect, perfect, perfect, perfect);
        }

        double naturalness = computeDimensionScore(
                sentences, totalSentences, dimensionTypes("naturalness"), true);
        double accuracy = computeDimensionScore(
                sentences, totalSentences, dimensionTypes("accuracy"), false);
        double fluency = computeDimensionScore(
                sentences, totalSentences, dimensionTypes("fluency"), false);
        double lexical = computeDimensionScore(
                sentences, totalSentences, dimensionTypes("lexical"), false);

        int naturalnessScore = clampRound(naturalness);
        int accuracyScore = clampRound(accuracy);
        int fluencyScore = clampRound(fluency);
        int lexicalScore = clampRound(lexical);
        int overall = clampRound(weightedOverall(
                naturalnessScore, accuracyScore, fluencyScore, lexicalScore));

        return new PerformanceScoreResult(overall, naturalnessScore, accuracyScore, fluencyScore, lexicalScore);
    }

    private double weightedOverall(int naturalness, int accuracy, int fluency, int lexical) {
        Map<String, PerformanceScoringProperties.DimensionConfig> dims = properties.getDimensions();
        if (CollectionUtils.isEmpty(dims)) {
            return (naturalness + accuracy + fluency + lexical) / 4.0;
        }
        double weightedSum = 0;
        double weightTotal = 0;
        weightedSum += weightOf(dims, "naturalness") * naturalness;
        weightedSum += weightOf(dims, "accuracy") * accuracy;
        weightedSum += weightOf(dims, "fluency") * fluency;
        weightedSum += weightOf(dims, "lexical") * lexical;
        weightTotal += weightOf(dims, "naturalness");
        weightTotal += weightOf(dims, "accuracy");
        weightTotal += weightOf(dims, "fluency");
        weightTotal += weightOf(dims, "lexical");
        if (weightTotal <= 0) {
            return (naturalness + accuracy + fluency + lexical) / 4.0;
        }
        return weightedSum / weightTotal;
    }

    private static double weightOf(
            Map<String, PerformanceScoringProperties.DimensionConfig> dims, String key) {
        PerformanceScoringProperties.DimensionConfig config = dims.get(key);
        return config == null ? 0 : config.getWeightInOverall();
    }

    private double computeDimensionScore(
            List<PerformanceScoringInput.SentenceErrors> allSentences,
            int totalSentences,
            Set<String> allowedTypes,
            boolean applyNaturalnessSentencePenalty) {
        if (CollectionUtils.isEmpty(allSentences) || CollectionUtils.isEmpty(allowedTypes)) {
            return properties.getMaxScore();
        }

        double totalWeightedPenalty = 0;
        int severeSentenceCount = 0;
        int naturalnessSentenceCount = 0;
        Set<String> naturalnessTypes = dimensionTypes("naturalness");

        for (PerformanceScoringInput.SentenceErrors sentence : allSentences) {
            double sentencePenalty = 0;
            boolean severe = false;
            boolean naturalnessHit = false;

            for (String typeKey : sentence.problemTypeKeys()) {
                if (!allowedTypes.contains(typeKey)) {
                    continue;
                }
                sentencePenalty += weightForType(typeKey);
                if (isFatalType(typeKey)) {
                    severe = true;
                }
                if (naturalnessTypes.contains(typeKey)) {
                    naturalnessHit = true;
                }
            }

            if (sentencePenalty <= 0) {
                continue;
            }
            totalWeightedPenalty += Math.min(sentencePenalty, properties.getMaxWeightedPenaltyPerSentence());
            if (severe) {
                severeSentenceCount++;
            }
            if (naturalnessHit) {
                naturalnessSentenceCount++;
            }
        }

        if (totalWeightedPenalty <= 0) {
            return properties.getMaxScore();
        }

        double effectiveSentences = totalSentences + properties.getDensitySmoothingK();
        double weightedDensity = totalWeightedPenalty / effectiveSentences;
        double penalty = properties.getDensityMultiplier()
                * (1 - Math.exp(-properties.getDensityDecay() * weightedDensity));
        double score = properties.getBaseScore() - penalty;

        double severeRatio = severeSentenceCount / effectiveSentences;
        score -= severeRatio * properties.getSevereSentencePenalty();

        if (applyNaturalnessSentencePenalty) {
            double naturalnessRatio = naturalnessSentenceCount / effectiveSentences;
            score -= naturalnessRatio * properties.getNaturalnessSentencePenalty();
        }

        return score;
    }

    private boolean isFatalType(String typeKey) {
        if (!StringUtils.hasText(typeKey)) {
            return false;
        }
        try {
            return ProblemType.valueOf(typeKey.trim()).getErrorLevel() == ErrorLevel.FATAL;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Set<String> dimensionTypes(String dimensionKey) {
        Map<String, PerformanceScoringProperties.DimensionConfig> dims = properties.getDimensions();
        if (CollectionUtils.isEmpty(dims)) {
            return Set.of();
        }
        PerformanceScoringProperties.DimensionConfig config = dims.get(dimensionKey);
        if (config == null || CollectionUtils.isEmpty(config.getTypes())) {
            return Set.of();
        }
        return new HashSet<>(config.getTypes());
    }

    private double weightForType(String typeKey) {
        if (!StringUtils.hasText(typeKey)) {
            return properties.getDefaultTypeWeight();
        }
        Map<String, Double> weights = properties.getTypeWeights();
        if (!CollectionUtils.isEmpty(weights) && weights.containsKey(typeKey)) {
            return weights.get(typeKey);
        }
        return properties.getDefaultTypeWeight();
    }

    private int clampRound(double value) {
        int rounded = (int) Math.round(value);
        return Math.max(properties.getMinScore(), Math.min(properties.getMaxScore(), rounded));
    }
}
