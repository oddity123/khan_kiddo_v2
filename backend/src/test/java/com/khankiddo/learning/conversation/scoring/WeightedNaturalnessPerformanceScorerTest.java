package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.config.PerformanceScoringProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedNaturalnessPerformanceScorerTest {

    private WeightedNaturalnessPerformanceScorer scorer;

    @BeforeEach
    void setUp() {
        PerformanceScoringProperties properties = new PerformanceScoringProperties();
        properties.setTypeWeights(defaultTypeWeights());
        properties.setDimensions(defaultDimensions());
        scorer = new WeightedNaturalnessPerformanceScorer(properties);
    }

    @Test
    void noErrors_returnsMaxScore() {
        PerformanceScoreResult result = scorer.score(new PerformanceScoringInput(10, List.of()));
        assertThat(result.overall()).isEqualTo(98);
        assertThat(result.naturalness()).isEqualTo(98);
    }

    @Test
    void sameInput_producesDeterministicScore() {
        PerformanceScoringInput input = new PerformanceScoringInput(
                8,
                List.of(
                        new PerformanceScoringInput.SentenceErrors(List.of("CHINGLISH", "TENSE")),
                        new PerformanceScoringInput.SentenceErrors(List.of("ARTICLE"))));
        PerformanceScoreResult first = scorer.score(input);
        PerformanceScoreResult second = scorer.score(input);
        assertThat(second).isEqualTo(first);
        assertThat(first.overall()).isBetween(60, 98);
    }

    @Test
    void severeErrors_scoreLowerThanMinorErrors() {
        PerformanceScoringInput severe = new PerformanceScoringInput(
                5,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("CHINESE", "INCOMPLETE"))));
        PerformanceScoringInput minor = new PerformanceScoringInput(
                5,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("ARTICLE", "TONE"))));
        assertThat(scorer.score(severe).overall()).isLessThan(scorer.score(minor).overall());
    }

    private static Map<String, Double> defaultTypeWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("CHINESE", 4.0);
        weights.put("INCOMPLETE", 3.5);
        weights.put("CHINGLISH", 3.2);
        weights.put("COLLOCATION", 3.0);
        weights.put("UNNATURAL", 2.8);
        weights.put("STRUCTURE", 2.8);
        weights.put("TENSE", 2.2);
        weights.put("ARTICLE", 1.2);
        weights.put("TONE", 1.4);
        return weights;
    }

    private static Map<String, PerformanceScoringProperties.DimensionConfig> defaultDimensions() {
        Map<String, PerformanceScoringProperties.DimensionConfig> dims = new LinkedHashMap<>();
        dims.put("naturalness", dimension(0.40, List.of("CHINGLISH", "COLLOCATION", "UNNATURAL")));
        dims.put("accuracy", dimension(0.25, List.of("TENSE", "ARTICLE", "STRUCTURE")));
        dims.put("fluency", dimension(0.20, List.of("INCOMPLETE", "CHINESE")));
        dims.put("lexical", dimension(0.15, List.of("COLLOCATION", "VOCABULARY")));
        return dims;
    }

    private static PerformanceScoringProperties.DimensionConfig dimension(double weight, List<String> types) {
        PerformanceScoringProperties.DimensionConfig config = new PerformanceScoringProperties.DimensionConfig();
        config.setWeightInOverall(weight);
        config.setTypes(types);
        return config;
    }
}
