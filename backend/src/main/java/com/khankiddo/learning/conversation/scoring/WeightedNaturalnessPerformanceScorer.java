package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.config.PerformanceScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于配置权重的口语自然度评分：加权错误密度 + 非线性扣分 + 句子占比修正。
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

        int overall = computeDimensionScoreInt(sentences, totalSentences, null);
        int naturalness = computeDimensionScoreInt(sentences, totalSentences, dimensionTypes("naturalness"));
        int accuracy = computeDimensionScoreInt(sentences, totalSentences, dimensionTypes("accuracy"));
        int fluency = computeDimensionScoreInt(sentences, totalSentences, dimensionTypes("fluency"));
        int lexical = computeDimensionScoreInt(sentences, totalSentences, dimensionTypes("lexical"));

        return new PerformanceScoreResult(overall, naturalness, accuracy, fluency, lexical);
    }

    private int computeDimensionScoreInt(
            List<PerformanceScoringInput.SentenceErrors> allSentences,
            int totalSentences,
            Set<String> allowedTypes) {
        return clampRound(computeDimensionScore(allSentences, totalSentences, allowedTypes));
    }

    private double computeDimensionScore(
            List<PerformanceScoringInput.SentenceErrors> allSentences,
            int totalSentences,
            Set<String> allowedTypes) {
        if (CollectionUtils.isEmpty(allSentences)) {
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
                if (allowedTypes != null && !allowedTypes.contains(typeKey)) {
                    continue;
                }
                double weight = weightForType(typeKey);
                sentencePenalty += weight;
                if (weight >= properties.getSevereWeightThreshold()) {
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

        if (totalWeightedPenalty <= 0 && allowedTypes != null) {
            return properties.getMaxScore();
        }

        double weightedDensity = totalWeightedPenalty / totalSentences;
        double penalty = properties.getDensityMultiplier()
                * (1 - Math.exp(-properties.getDensityDecay() * weightedDensity));
        double score = properties.getBaseScore() - penalty;

        double severeRatio = (double) severeSentenceCount / totalSentences;
        double naturalnessRatio = (double) naturalnessSentenceCount / totalSentences;
        score -= severeRatio * properties.getSevereSentencePenalty();
        score -= naturalnessRatio * properties.getNaturalnessSentencePenalty();

        return score;
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
