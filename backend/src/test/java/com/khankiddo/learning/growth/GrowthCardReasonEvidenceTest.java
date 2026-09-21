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
}
