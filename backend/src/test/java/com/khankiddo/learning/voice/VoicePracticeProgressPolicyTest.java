package com.khankiddo.learning.voice;

import com.khankiddo.learning.growth.GrowthCardScheduler;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VoicePracticeProgressPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void independentSuccess_promotesOneStepOnly() {
        Optional<GrowthCardScheduler.ReviewResult> up1 = VoicePracticeProgressPolicy.promotion(
                "unfamiliar", VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS, TODAY);
        assertThat(up1).isPresent();
        assertThat(up1.get().status()).isEqualTo("fuzzy");
        assertThat(up1.get().nextDueAt()).isEqualTo(TODAY.plusDays(4));

        Optional<GrowthCardScheduler.ReviewResult> up2 = VoicePracticeProgressPolicy.promotion(
                "fuzzy", VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS, TODAY);
        assertThat(up2).isPresent();
        assertThat(up2.get().status()).isEqualTo("mastered");
    }

    @Test
    void masteredNeverChanges_andNoDemotion() {
        assertThat(VoicePracticeProgressPolicy.promotion(
                "mastered", VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS, TODAY)).isEmpty();
        for (VoicePracticeAttemptOutcome outcome : VoicePracticeAttemptOutcome.values()) {
            if (outcome == VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS) {
                continue;
            }
            for (String status : new String[]{"unfamiliar", "fuzzy", "mastered"}) {
                assertThat(VoicePracticeProgressPolicy.promotion(status, outcome, TODAY))
                        .as("%s on %s", outcome, status)
                        .isEmpty();
            }
        }
    }

    @Test
    void unknownStatusIsLeftAlone() {
        assertThat(VoicePracticeProgressPolicy.promotion(
                "weird", VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS, TODAY)).isEmpty();
        assertThat(VoicePracticeProgressPolicy.promotion(
                null, VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS, TODAY)).isEmpty();
    }

    @Test
    void alreadyPromotedInSession_blocksFurtherPromotion() {
        assertThat(VoicePracticeProgressPolicy.promotion(
                "fuzzy",
                VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS,
                TODAY,
                true)).isEmpty();
        assertThat(VoicePracticeProgressPolicy.promotion(
                "unfamiliar",
                VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS,
                TODAY,
                true)).isEmpty();
    }

    @Test
    void outcomeParsesOnlyWireValues() {
        assertThat(VoicePracticeAttemptOutcome.fromWire(" independent_success "))
                .contains(VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS);
        assertThat(VoicePracticeAttemptOutcome.fromWire("I understand")).isEmpty();
        assertThat(VoicePracticeAttemptOutcome.fromWire("")).isEmpty();
        assertThat(VoicePracticeAttemptOutcome.fromWire(null)).isEmpty();
    }
}
