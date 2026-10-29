package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoicePracticeMaterialSnapshotSupportTest {

    private final VoicePracticeMaterialSnapshotSupport support =
            new VoicePracticeMaterialSnapshotSupport(new ObjectMapper());

    @Test
    void normalize_dedupesTrimsAndCaps() {
        VoicePracticeMaterialSnapshot raw = new VoicePracticeMaterialSnapshot(
                java.util.Arrays.asList(" a ", "a", "b", "", null),
                List.of("x", "x", "y"));
        VoicePracticeMaterialSnapshot normalized = support.normalize(raw);
        assertThat(normalized.cardIds()).containsExactly("a", "b");
        assertThat(normalized.analysisIds()).containsExactly("x", "y");
    }

    @Test
    void normalize_rejectsTooManyCards() {
        List<String> many = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "c" + i)
                .toList();
        assertThatThrownBy(() -> support.normalize(new VoicePracticeMaterialSnapshot(many, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成长卡");
    }

    @Test
    void roundTrip_json() throws Exception {
        VoicePracticeMaterialSnapshot snap = support.normalize(
                new VoicePracticeMaterialSnapshot(List.of("c1"), List.of("a1")));
        String json = support.toJson(snap);
        VoicePracticeMaterialSnapshot back = support.fromJson(json);
        assertThat(back.cardIds()).containsExactly("c1");
        assertThat(back.analysisIds()).containsExactly("a1");
    }

    @Test
    void fromJson_nullOrBlank_isEmpty() throws Exception {
        assertThat(support.fromJson(null).cardIds()).isEmpty();
        assertThat(support.fromJson("").analysisIds()).isEmpty();
    }
}
