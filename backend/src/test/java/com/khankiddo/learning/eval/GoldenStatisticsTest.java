package com.khankiddo.learning.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GoldenStatisticsTest {

    private static final Function<String, String> FAMILY = id -> switch (id) {
        case "SVA_THIRD_PERSON", "AGREEMENT_OTHER" -> "FAM_AGREEMENT";
        case "ARTICLE_A_AN", "ARTICLE_OTHER" -> "FAM_ARTICLE";
        case "FLUENCY_REDUNDANCY" -> "FAM_FLUENCY";
        default -> "FAM_UNKNOWN";
    };

    @Test
    void normalize_trimsAndCollapsesWhitespace() {
        assertThat(GoldenStatistics.normalize("  It   have  a example. "))
                .isEqualTo("It have a example.");
    }

    @Test
    void sentenceDetection_tpTnFpFn() {
        var input = List.of(
                new GoldenStatistics.InputUtterance("u1", "It have a example."),
                new GoldenStatistics.InputUtterance("u2", "That makes sense."),
                new GoldenStatistics.InputUtterance("u3", "I go yesterday."),
                new GoldenStatistics.InputUtterance("u4", "Hello there."));
        var gold = List.of(
                new GoldenStatistics.GoldUtterance("u1", true, List.of(
                        new GoldenStatistics.GoldError("e1", "SVA_THIRD_PERSON",
                                List.of("SVA_THIRD_PERSON", "AGREEMENT_OTHER")))),
                new GoldenStatistics.GoldUtterance("u2", false, List.of()),
                new GoldenStatistics.GoldUtterance("u3", true, List.of(
                        new GoldenStatistics.GoldError("e2", "PAST_SIMPLE_DONE",
                                List.of("PAST_SIMPLE_DONE")))),
                new GoldenStatistics.GoldUtterance("u4", false, List.of()));
        var actual = List.of(
                new GoldenStatistics.ActualItem("It have a example.", List.of("SVA_THIRD_PERSON")),
                new GoldenStatistics.ActualItem("That makes sense.", List.of("STRUCTURE_OTHER")),
                new GoldenStatistics.ActualItem("Ghost sentence.", List.of("LEXICAL_GAP")));

        GoldenStatistics.CaseReport report =
                GoldenStatistics.evaluate(input, gold, actual, FAMILY);

        assertThat(report.tp()).isEqualTo(1);
        assertThat(report.fp()).isEqualTo(1);
        assertThat(report.fn()).isEqualTo(1);
        assertThat(report.tn()).isEqualTo(1);
        assertThat(report.precision()).isCloseTo(0.5, within(1e-9));
        assertThat(report.recall()).isCloseTo(0.5, within(1e-9));
        assertThat(report.f1()).isCloseTo(0.5, within(1e-9));
        assertThat(report.falsePositiveIds()).containsExactly("u2");
        assertThat(report.falseNegativeIds()).containsExactly("u3");
        assertThat(report.unalignedActual()).containsExactly("Ghost sentence.");
        // FN 不进 leafAcc 分母：仅 TP 的 u1 一条 gold error
        assertThat(report.goldErrorCount()).isEqualTo(1);
        assertThat(report.leafHits()).isEqualTo(1);
        assertThat(report.leafAccuracy()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void leafAndFamily_greedyAcceptableIds() {
        var input = List.of(new GoldenStatistics.InputUtterance("u1", "It have a example."));
        var gold = List.of(new GoldenStatistics.GoldUtterance("u1", true, List.of(
                new GoldenStatistics.GoldError("e1", "SVA_THIRD_PERSON",
                        List.of("SVA_THIRD_PERSON", "AGREEMENT_OTHER")),
                new GoldenStatistics.GoldError("e2", "ARTICLE_A_AN",
                        List.of("ARTICLE_A_AN")))));
        var actual = List.of(new GoldenStatistics.ActualItem(
                "It have a example.", List.of("AGREEMENT_OTHER", "ARTICLE_OTHER")));

        GoldenStatistics.CaseReport report =
                GoldenStatistics.evaluate(input, gold, actual, FAMILY);

        assertThat(report.tp()).isEqualTo(1);
        assertThat(report.goldErrorCount()).isEqualTo(2);
        assertThat(report.leafHits()).isEqualTo(1);
        assertThat(report.familyHits()).isEqualTo(2);
        assertThat(report.leafAccuracy()).isCloseTo(0.5, within(1e-9));
        assertThat(report.familyAccuracy()).isEqualTo(1.0);
        // ARTICLE_OTHER 未进 e2 的 acceptablePointIds，叶子层计为多余检出
        assertThat(report.surplusActualErrors()).isEqualTo(1);
    }

    @Test
    void surplusActualErrors_countedWhenExtraPointIds() {
        var input = List.of(new GoldenStatistics.InputUtterance("u1", "Bad sentence."));
        var gold = List.of(new GoldenStatistics.GoldUtterance("u1", true, List.of(
                new GoldenStatistics.GoldError("e1", "SVA_THIRD_PERSON", List.of("SVA_THIRD_PERSON")))));
        var actual = List.of(new GoldenStatistics.ActualItem(
                "Bad sentence.", List.of("SVA_THIRD_PERSON", "FLUENCY_REDUNDANCY")));

        GoldenStatistics.CaseReport report =
                GoldenStatistics.evaluate(input, gold, actual, FAMILY);

        assertThat(report.leafHits()).isEqualTo(1);
        assertThat(report.surplusActualErrors()).isEqualTo(1);
    }
}
