package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.mapper.VoicePracticeEventMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;
import com.khankiddo.learning.voice.tool.AppendPracticeNoteToolHandler;
import com.khankiddo.learning.voice.tool.FocusPracticeCardToolHandler;
import com.khankiddo.learning.voice.tool.RecordPracticeAttemptToolHandler;
import com.khankiddo.learning.voice.tool.VoiceLearningToolSupport;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolContext;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceLearningToolHandlersTest {

    private static final long USER = 7L;
    private static final long SESSION = 99L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final VoiceRealtimeToolContext ctx = new VoiceRealtimeToolContext(USER, SESSION);

    private VoicePracticeMaterialAccess materialAccess;
    private GrowthCardStore cardStore;
    private VoicePracticeEventMapper eventMapper;
    private VoiceLearningToolSupport support;

    @BeforeEach
    void setUp() {
        materialAccess = mock(VoicePracticeMaterialAccess.class);
        cardStore = mock(GrowthCardStore.class);
        eventMapper = mock(VoicePracticeEventMapper.class);
        support = new VoiceLearningToolSupport(mapper, materialAccess, cardStore);
        when(materialAccess.requireSnapshot(USER, SESSION))
                .thenReturn(new VoicePracticeMaterialSnapshot(List.of("c1", "c2"), List.of("a1")));
        when(cardStore.findOwned("c1", USER)).thenReturn(Optional.of(card("c1", "unfamiliar")));
        when(cardStore.findOwned("c2", USER)).thenReturn(Optional.of(card("c2", "fuzzy")));
        when(eventMapper.insert(any())).thenAnswer(inv -> {
            VoicePracticeEvent row = inv.getArgument(0);
            row.setId(42L);
            return 1;
        });
    }

    private static GrowthCard card(String id, String status) {
        return GrowthCard.builder()
                .cardId(id).userId(USER).type("habit").status(status)
                .front("front-" + id).back("back-" + id).build();
    }

    private JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }

    // ---- focus ----

    @Test
    void focus_inSnapshot_persistsAndEmitsEvent() throws Exception {
        FocusPracticeCardToolHandler handler = new FocusPracticeCardToolHandler(support, eventMapper);
        VoiceRealtimeToolResult result = handler.execute(ctx, "{\"cardId\":\"c1\",\"promptHint\":\"ask about yesterday\"}");

        JsonNode out = json(result.outputJson());
        assertThat(out.path("ok").asBoolean()).isTrue();
        assertThat(out.path("cardId").asText()).isEqualTo("c1");
        assertThat(out.path("front").asText()).isEqualTo("front-c1");
        assertThat(out.path("back").asText()).isEqualTo("back-c1");
        assertThat(out.path("status").asText()).isEqualTo("unfamiliar");

        ArgumentCaptor<VoicePracticeEvent> row = ArgumentCaptor.forClass(VoicePracticeEvent.class);
        verify(eventMapper).insert(row.capture());
        assertThat(row.getValue().getEventType()).isEqualTo(VoicePracticeEvent.TYPE_FOCUS);
        assertThat(row.getValue().getPracticeSessionId()).isEqualTo(SESSION);
        assertThat(row.getValue().getUserId()).isEqualTo(USER);
        assertThat(row.getValue().getNoteText()).isEqualTo("ask about yesterday");

        assertThat(result.learningEvent()).isNotNull();
        assertThat(result.learningEvent().type()).isEqualTo(VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_FOCUS);
        assertThat(result.learningEvent().payload().path("cardId").asText()).isEqualTo("c1");
        assertThat(result.learningEvent().payload().path("promptHint").asText()).isEqualTo("ask about yesterday");
    }

    @Test
    void focus_outOfSnapshot_isRejectedWithoutPersistOrEvent() throws Exception {
        FocusPracticeCardToolHandler handler = new FocusPracticeCardToolHandler(support, eventMapper);
        VoiceRealtimeToolResult result = handler.execute(ctx, "{\"cardId\":\"foreign\"}");

        JsonNode out = json(result.outputJson());
        assertThat(out.path("ok").asBoolean()).isFalse();
        assertThat(out.path("error").asText()).isEqualTo("card_not_in_session");
        assertThat(result.learningEvent()).isNull();
        verify(eventMapper, never()).insert(any());
        verify(cardStore, never()).findOwned(eq("foreign"), anyLong());
    }

    @Test
    void focus_missingCardId_isRejected() throws Exception {
        FocusPracticeCardToolHandler handler = new FocusPracticeCardToolHandler(support, eventMapper);
        JsonNode out = json(handler.execute(ctx, "{}").outputJson());
        assertThat(out.path("error").asText()).isEqualTo("card_id_required");
        JsonNode bad = json(handler.execute(ctx, "not json").outputJson());
        assertThat(bad.path("error").asText()).isEqualTo("invalid_arguments");
    }

    // ---- attempt ----

    private RecordPracticeAttemptToolHandler attemptHandler() {
        when(eventMapper.countPromotionsForCard(SESSION, USER, "c1")).thenReturn(0);
        when(eventMapper.countPromotionsForCard(SESSION, USER, "c2")).thenReturn(0);
        VoicePracticeAttemptRecorder recorder =
                new VoicePracticeAttemptRecorder(eventMapper, cardStore, support);
        return new RecordPracticeAttemptToolHandler(support, recorder);
    }

    @Test
    void attempt_independentSuccess_unfamiliarPromotesToFuzzy() throws Exception {
        when(cardStore.updateReview(eq("c1"), eq(USER), eq("fuzzy"), any(LocalDate.class)))
                .thenReturn(card("c1", "fuzzy"));

        VoiceRealtimeToolResult result = attemptHandler().execute(ctx, """
                {"cardId":"c1","outcome":"independent_success","userUtterance":"I went there yesterday","reason":"no prompt"}
                """);

        JsonNode out = json(result.outputJson());
        assertThat(out.path("ok").asBoolean()).isTrue();
        assertThat(out.path("attemptId").asLong()).isEqualTo(42L);
        assertThat(out.path("newStatus").asText()).isEqualTo("fuzzy");
        verify(cardStore).updateReview(eq("c1"), eq(USER), eq("fuzzy"), any(LocalDate.class));

        assertThat(result.learningEvent().type()).isEqualTo(VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_ATTEMPT);
        JsonNode payload = result.learningEvent().payload();
        assertThat(payload.path("statusBefore").asText()).isEqualTo("unfamiliar");
        assertThat(payload.path("statusAfter").asText()).isEqualTo("fuzzy");
        assertThat(payload.path("cardFront").asText()).isEqualTo("front-c1");
    }

    @Test
    void attempt_independentSuccess_fuzzyPromotesToMasteredOnly() throws Exception {
        when(cardStore.updateReview(eq("c2"), eq(USER), eq("mastered"), any()))
                .thenReturn(card("c2", "mastered"));

        JsonNode out = json(attemptHandler().execute(ctx,
                "{\"cardId\":\"c2\",\"outcome\":\"independent_success\"}").outputJson());
        assertThat(out.path("newStatus").asText()).isEqualTo("mastered");
    }

    @Test
    void attempt_secondIndependentSuccessSameCard_doesNotPromoteAgain() throws Exception {
        when(eventMapper.countPromotionsForCard(SESSION, USER, "c1")).thenReturn(1);
        when(cardStore.findOwned("c1", USER)).thenReturn(Optional.of(card("c1", "fuzzy")));
        VoicePracticeAttemptRecorder recorder =
                new VoicePracticeAttemptRecorder(eventMapper, cardStore, support);
        RecordPracticeAttemptToolHandler handler = new RecordPracticeAttemptToolHandler(support, recorder);

        JsonNode out = json(handler.execute(ctx,
                "{\"cardId\":\"c1\",\"outcome\":\"independent_success\"}").outputJson());
        assertThat(out.path("ok").asBoolean()).isTrue();
        assertThat(out.has("newStatus")).isFalse();
        assertThat(out.path("status").asText()).isEqualTo("fuzzy");
        verify(cardStore, never()).updateReview(anyString(), anyLong(), anyString(), any());
        verify(eventMapper).insert(any());
    }

    @Test
    void attempt_nonIndependentOutcomes_neverTouchProgress() throws Exception {
        for (String outcome : new String[]{"prompted", "uncertain", "miss"}) {
            VoiceRealtimeToolResult result = attemptHandler().execute(ctx,
                    "{\"cardId\":\"c2\",\"outcome\":\"" + outcome + "\"}");
            JsonNode out = json(result.outputJson());
            assertThat(out.path("ok").asBoolean()).as(outcome).isTrue();
            assertThat(out.has("newStatus")).as(outcome).isFalse();
            assertThat(out.path("status").asText()).isEqualTo("fuzzy");
            assertThat(result.learningEvent().payload().path("statusAfter").isNull()).isTrue();
        }
        verify(cardStore, never()).updateReview(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void attempt_invalidOutcomeOrForeignCard_isRejected() throws Exception {
        JsonNode badOutcome = json(attemptHandler().execute(ctx,
                "{\"cardId\":\"c1\",\"outcome\":\"I understand\"}").outputJson());
        assertThat(badOutcome.path("error").asText()).isEqualTo("invalid_outcome");

        VoiceRealtimeToolResult foreign = attemptHandler().execute(ctx,
                "{\"cardId\":\"nope\",\"outcome\":\"independent_success\"}");
        assertThat(json(foreign.outputJson()).path("error").asText()).isEqualTo("card_not_in_session");
        assertThat(foreign.learningEvent()).isNull();
        verify(eventMapper, never()).insert(any());
        verify(cardStore, never()).updateReview(anyString(), anyLong(), anyString(), any());
    }

    // ---- note ----

    private AppendPracticeNoteToolHandler noteHandler() {
        return new AppendPracticeNoteToolHandler(support, eventMapper);
    }

    @Test
    void note_requiresText() throws Exception {
        for (String args : new String[]{"{}", "{\"text\":\"   \"}", "{\"cardId\":\"c1\"}"}) {
            VoiceRealtimeToolResult result = noteHandler().execute(ctx, args);
            assertThat(json(result.outputJson()).path("error").asText()).isEqualTo("text_required");
            assertThat(result.learningEvent()).isNull();
        }
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void note_withScopedCardAndAnalysis_persistsAndEmits() throws Exception {
        VoiceRealtimeToolResult result = noteHandler().execute(ctx,
                "{\"text\":\"watch past tense\",\"cardId\":\"c1\",\"analysisId\":\"a1\"}");
        JsonNode out = json(result.outputJson());
        assertThat(out.path("ok").asBoolean()).isTrue();
        assertThat(out.path("noteId").asLong()).isEqualTo(42L);

        ArgumentCaptor<VoicePracticeEvent> row = ArgumentCaptor.forClass(VoicePracticeEvent.class);
        verify(eventMapper).insert(row.capture());
        assertThat(row.getValue().getEventType()).isEqualTo(VoicePracticeEvent.TYPE_NOTE);
        assertThat(row.getValue().getNoteText()).isEqualTo("watch past tense");
        assertThat(row.getValue().getCardId()).isEqualTo("c1");
        assertThat(row.getValue().getAnalysisId()).isEqualTo("a1");
        assertThat(result.learningEvent().type()).isEqualTo(VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_NOTE);
        assertThat(result.learningEvent().payload().path("cardFront").asText()).isEqualTo("front-c1");
    }

    @Test
    void note_textOnly_ok_butForeignScopeRejected() throws Exception {
        assertThat(json(noteHandler().execute(ctx, "{\"text\":\"hello\"}").outputJson())
                .path("ok").asBoolean()).isTrue();

        JsonNode foreignCard = json(noteHandler().execute(ctx,
                "{\"text\":\"x\",\"cardId\":\"zzz\"}").outputJson());
        assertThat(foreignCard.path("error").asText()).isEqualTo("card_not_in_session");

        JsonNode foreignAnalysis = json(noteHandler().execute(ctx,
                "{\"text\":\"x\",\"analysisId\":\"zzz\"}").outputJson());
        assertThat(foreignAnalysis.path("error").asText()).isEqualTo("analysis_not_in_session");
    }

    @Test
    void note_longTextIsClipped() throws Exception {
        String longText = "a".repeat(900);
        noteHandler().execute(ctx, "{\"text\":\"" + longText + "\"}");
        ArgumentCaptor<VoicePracticeEvent> row = ArgumentCaptor.forClass(VoicePracticeEvent.class);
        verify(eventMapper).insert(row.capture());
        assertThat(row.getValue().getNoteText()).hasSize(VoiceLearningToolSupport.MAX_NOTE);
    }
}
