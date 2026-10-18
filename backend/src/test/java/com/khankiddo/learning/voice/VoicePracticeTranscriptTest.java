package com.khankiddo.learning.voice;

import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoicePracticeTranscriptTest {

    @Test
    void formatsCompletedUserAndAssistantLines() {
        String text = VoicePracticeTranscript.format(List.of(
                new VoicePracticeCaption("user", " I go yesterday\n"),
                new VoicePracticeCaption("assistant", "What did you do?"),
                new VoicePracticeCaption("user", "   ")));
        assertThat(text).isEqualTo("User: I go yesterday\nAssistant: What did you do?");
    }

    @Test
    void rejectsUnknownRoleSoPartialsCannotBeSmuggled() {
        assertThatThrownBy(() -> VoicePracticeTranscript.format(List.of(
                new VoicePracticeCaption("partial", "half a sentence"))))
                .isInstanceOf(BadRequestException.class);
    }
}
