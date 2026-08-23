package com.khankiddo.learning.knowledge;

import com.khankiddo.learning.dto.KnowledgeFamilyStat;
import com.khankiddo.learning.model.PointIdCount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgePointStatsSupportTest {

    private static final PointDictionary DICTIONARY =
            PointDictionary.loadFromClasspath("knowledge/point-dictionary-v1.json");

    @Test
    void aggregatesPointCountsByFamily() {
        List<PointIdCount> rows = List.of(
                row("PAST_SIMPLE_DONE", 3L),
                row("PRESENT_PERFECT_FORM", 2L),
                row("ARTICLE_GENERIC_ZERO", 4L));

        List<KnowledgeFamilyStat> stats = KnowledgePointStatsSupport.aggregateFamilies(rows, DICTIONARY, 10);

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).getFamilyId()).isEqualTo("FAM_TENSE");
        assertThat(stats.get(0).getCount()).isEqualTo(5L);
        assertThat(stats.get(1).getFamilyId()).isEqualTo("FAM_ARTICLE");
        assertThat(stats.get(1).getCount()).isEqualTo(4L);
    }

    @Test
    void mergePointFilters_unionsPointIdsAndFamilyIds() {
        List<String> merged = KnowledgePointStatsSupport.mergePointFilters(
                DICTIONARY,
                List.of("PAST_SIMPLE_DONE"),
                List.of("FAM_ARTICLE"));

        assertThat(merged).contains("PAST_SIMPLE_DONE", "ARTICLE_GENERIC_ZERO");
        assertThat(merged.size()).isGreaterThan(2);
    }

    private static PointIdCount row(String pointId, long count) {
        PointIdCount row = new PointIdCount();
        row.setPointId(pointId);
        row.setCount(count);
        return row;
    }
}
