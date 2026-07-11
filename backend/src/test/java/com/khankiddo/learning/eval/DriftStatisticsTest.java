package com.khankiddo.learning.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DriftStatisticsTest {

    private static DriftStatistics.DriftSample sample(
            int sentences, int errors, int score,
            Map<String, Integer> types, Set<String> flagged) {
        return new DriftStatistics.DriftSample(
                sentences, errors, score,
                Map.of("naturalness", score, "accuracy", score, "fluency", score, "lexical", score),
                types, flagged);
    }

    @Test
    void intStat_computesMinMaxMeanStdDev() {
        DriftStatistics.IntStat stat = DriftStatistics.intStat(List.of(2, 4, 6));
        assertThat(stat.min()).isEqualTo(2);
        assertThat(stat.max()).isEqualTo(6);
        assertThat(stat.mean()).isEqualTo(4.0);
        assertThat(stat.range()).isEqualTo(4);
        // population stddev of {2,4,6} = sqrt(8/3) ≈ 1.633
        assertThat(stat.stdDev()).isCloseTo(1.633, within(0.01));
    }

    @Test
    void identicalRuns_areStable() {
        Map<String, Integer> types = Map.of("Tense", 2, "Article", 1);
        Set<String> flagged = Set.of("s1", "s2");
        List<DriftStatistics.DriftSample> runs = List.of(
                sample(10, 3, 82, types, flagged),
                sample(10, 3, 82, types, flagged),
                sample(10, 3, 82, types, flagged));

        DriftStatistics.ConversationDriftReport report =
                DriftStatistics.analyzeConversation("c1", runs);

        assertThat(report.performanceScore().range()).isZero();
        assertThat(report.avgTypeDistributionJaccard()).isEqualTo(1.0);
        assertThat(report.sentenceFlagInstability()).isZero();
        assertThat(report.verdict()).isEqualTo("STABLE");
    }

    @Test
    void divergentScores_flaggedAsHighDrift() {
        List<DriftStatistics.DriftSample> runs = List.of(
                sample(10, 3, 88, Map.of("Tense", 1), Set.of("s1")),
                sample(12, 8, 70, Map.of("Chinglish", 3, "Structure", 2), Set.of("s2", "s3")),
                sample(9, 5, 79, Map.of("Article", 2, "Tense", 1), Set.of("s1", "s4")));

        DriftStatistics.ConversationDriftReport report =
                DriftStatistics.analyzeConversation("c2", runs);

        assertThat(report.performanceScore().range()).isGreaterThan(DriftStatistics.SCORE_RANGE_MODERATE);
        assertThat(report.verdict()).isEqualTo("HIGH_DRIFT");
    }

    @Test
    void sentenceFlagInstability_reflectsFlipping() {
        // s_stable 出现在全部 3 次；s_flip 只出现在 1 次 → 不稳定占比 1/2
        List<DriftStatistics.DriftSample> runs = List.of(
                sample(5, 2, 80, Map.of("Tense", 1), Set.of("s_stable", "s_flip")),
                sample(5, 1, 80, Map.of("Tense", 1), Set.of("s_stable")),
                sample(5, 1, 80, Map.of("Tense", 1), Set.of("s_stable")));

        double instability = DriftStatistics.sentenceFlagInstability(runs);

        assertThat(instability).isEqualTo(0.5);
    }

    @Test
    void multisetJaccard_penalizesCountDifferences() {
        // 类型集合相同但计数不同：{Tense:1} vs {Tense:3} → 交/并 = 1/3
        List<DriftStatistics.DriftSample> runs = List.of(
                sample(5, 1, 80, Map.of("Tense", 1), Set.of("s1")),
                sample(5, 3, 80, Map.of("Tense", 3), Set.of("s1")));

        double jaccard = DriftStatistics.averagePairwiseMultisetJaccard(runs);

        assertThat(jaccard).isCloseTo(1.0 / 3.0, within(0.001));
    }

    @Test
    void corpusReport_aggregatesWorstCase() {
        Map<String, List<DriftStatistics.DriftSample>> corpus = Map.of(
                "stable", List.of(
                        sample(10, 3, 82, Map.of("Tense", 1), Set.of("s1")),
                        sample(10, 3, 82, Map.of("Tense", 1), Set.of("s1"))),
                "drifting", List.of(
                        sample(10, 2, 90, Map.of("Article", 1), Set.of("s2")),
                        sample(10, 9, 65, Map.of("Chinglish", 4), Set.of("s3"))));

        DriftStatistics.CorpusDriftReport report = DriftStatistics.analyzeCorpus(corpus);

        assertThat(report.perConversation()).hasSize(2);
        assertThat(report.worstScoreRange().max()).isEqualTo(25);
        assertThat(report.overallVerdict()).isEqualTo("HIGH_DRIFT");
    }

    @Test
    void singleRun_isTriviallyStable() {
        DriftStatistics.ConversationDriftReport report = DriftStatistics.analyzeConversation(
                "c", List.of(sample(10, 3, 82, Map.of("Tense", 1), Set.of("s1"))));

        assertThat(report.performanceScore().range()).isZero();
        assertThat(report.avgTypeDistributionJaccard()).isEqualTo(1.0);
        assertThat(report.verdict()).isEqualTo("STABLE");
    }
}
