package com.khankiddo.learning.growth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrowthCardReasonEvidenceTest {

    @Test
    void toEvidenceJson_nullOrBlank_returnsNull() {
        assertThat(GrowthCardReasonEvidence.toEvidenceJson(null)).isNull();
        assertThat(GrowthCardReasonEvidence.toEvidenceJson("")).isNull();
        assertThat(GrowthCardReasonEvidence.toEvidenceJson("   ")).isNull();
    }

    @Test
    void toEvidenceJson_escapesQuotesAndWritesReason() {
        assertThat(GrowthCardReasonEvidence.toEvidenceJson("感到…用 \"-ed\""))
                .isEqualTo("{\"reason\":\"感到…用 \\\"-ed\\\"\"}");
    }

    @Test
    void parseReason_readsEscapedJson() {
        String json = GrowthCardReasonEvidence.toEvidenceJson("感到…用 \"-ed\"");
        assertThat(GrowthCardReasonEvidence.parseReason(json)).isEqualTo("感到…用 \"-ed\"");
    }

    @Test
    void parseReason_nullBlankOrInvalid_returnsNull() {
        assertThat(GrowthCardReasonEvidence.parseReason(null)).isNull();
        assertThat(GrowthCardReasonEvidence.parseReason("")).isNull();
        assertThat(GrowthCardReasonEvidence.parseReason("not-json")).isNull();
        assertThat(GrowthCardReasonEvidence.parseReason("{\"other\":1}")).isNull();
        assertThat(GrowthCardReasonEvidence.parseReason("{\"reason\":\"\"}")).isNull();
    }
}
