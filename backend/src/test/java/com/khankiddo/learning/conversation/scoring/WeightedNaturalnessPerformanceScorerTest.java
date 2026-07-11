package com.khankiddo.learning.conversation.scoring;

import com.khankiddo.learning.config.PerformanceScoringProperties;
import com.khankiddo.learning.model.enums.ErrorLevel;
import com.khankiddo.learning.model.enums.ProblemType;
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
        properties.setMinScore(45);
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
        assertThat(first.overall()).isBetween(45, 98);
    }

    @Test
    void severeErrors_scoreLowerThanMinorErrors() {
        PerformanceScoringInput severe = new PerformanceScoringInput(
                5,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("STRUCTURE", "TENSE", "INCOMPLETE"))));
        PerformanceScoringInput minor = new PerformanceScoringInput(
                5,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("ARTICLE", "TONE"))));
        assertThat(scorer.score(severe).overall()).isLessThan(scorer.score(minor).overall());
    }

    @Test
    void overall_isWeightedAverageOfDimensionScores() {
        PerformanceScoringInput input = new PerformanceScoringInput(
                10,
                List.of(
                        new PerformanceScoringInput.SentenceErrors(List.of("CHINGLISH")),
                        new PerformanceScoringInput.SentenceErrors(List.of("TENSE")),
                        new PerformanceScoringInput.SentenceErrors(List.of("INCOMPLETE")),
                        new PerformanceScoringInput.SentenceErrors(List.of("VOCABULARY"))));

        PerformanceScoreResult result = scorer.score(input);

        double expectedOverall = (result.naturalness() * 0.40
                + result.accuracy() * 0.25
                + result.fluency() * 0.20
                + result.lexical() * 0.15) / 1.0;
        assertThat(result.overall()).isEqualTo((int) Math.round(expectedOverall));
    }

    @Test
    void fatalTense_triggersSeverePenaltyInAccuracyDimension() {
        PerformanceScoringInput tenseOnly = new PerformanceScoringInput(
                5,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("TENSE"))));
        PerformanceScoringInput articleOnly = new PerformanceScoringInput(
                5,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("ARTICLE"))));

        assertThat(scorer.score(tenseOnly).accuracy()).isLessThan(scorer.score(articleOnly).accuracy());
    }

    @Test
    void preposition_isBasicNotFatal() {
        assertThat(ProblemType.PREPOSITION.getErrorLevel()).isEqualTo(ErrorLevel.BASIC);
    }

    @Test
    void naturalnessPenalty_doesNotDeflateAccuracyWhenOnlyChinglishPresent() {
        PerformanceScoringInput chinglishOnly = new PerformanceScoringInput(
                8,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("CHINGLISH"))));

        PerformanceScoreResult result = scorer.score(chinglishOnly);

        assertThat(result.naturalness()).isLessThan(98);
        assertThat(result.accuracy()).isEqualTo(98);
    }

    @Test
    void shortConversation_isSmootherThanWithoutSmoothing() {
        PerformanceScoringProperties smooth = new PerformanceScoringProperties();
        smooth.setMinScore(45);
        smooth.setDensitySmoothingK(2.0);
        smooth.setTypeWeights(defaultTypeWeights());
        smooth.setDimensions(defaultDimensions());

        PerformanceScoringProperties harsh = new PerformanceScoringProperties();
        harsh.setMinScore(45);
        harsh.setDensitySmoothingK(0.0);
        harsh.setTypeWeights(defaultTypeWeights());
        harsh.setDimensions(defaultDimensions());

        PerformanceScoringInput input = new PerformanceScoringInput(
                1,
                List.of(new PerformanceScoringInput.SentenceErrors(List.of("INCOMPLETE", "STRUCTURE"))));

        int smoothScore = new WeightedNaturalnessPerformanceScorer(smooth).score(input).overall();
        int harshScore = new WeightedNaturalnessPerformanceScorer(harsh).score(input).overall();
        assertThat(smoothScore).isGreaterThan(harshScore);
    }

    private static Map<String, Double> defaultTypeWeights() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("INCOMPLETE", 3.5);
        weights.put("CHINGLISH", 3.2);
        weights.put("COLLOCATION", 3.0);
        weights.put("UNNATURAL", 2.8);
        weights.put("STRUCTURE", 2.8);
        weights.put("TENSE", 2.2);
        weights.put("ARTICLE", 1.2);
        weights.put("TONE", 1.4);
        weights.put("VOCABULARY", 1.8);
        return weights;
    }

    private static Map<String, PerformanceScoringProperties.DimensionConfig> defaultDimensions() {
        Map<String, PerformanceScoringProperties.DimensionConfig> dims = new LinkedHashMap<>();
        dims.put("naturalness", dimension(0.40, List.of("CHINGLISH", "COLLOCATION", "UNNATURAL", "TONE")));
        dims.put("accuracy", dimension(0.25, List.of("TENSE", "ARTICLE", "STRUCTURE")));
        dims.put("fluency", dimension(0.20, List.of("INCOMPLETE")));
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
