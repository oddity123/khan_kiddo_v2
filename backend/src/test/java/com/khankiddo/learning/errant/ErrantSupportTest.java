package com.khankiddo.learning.errant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.dto.conversation.AnalysisItemDto;
import com.khankiddo.learning.dto.conversation.SentenceEditDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrantSupportTest {

    @Test
    void tokenizeSplitsOnWhitespace() {
        assertEquals(List.of("This", "are", "a", "test."), ErrantTokenSupport.tokenize("This are a test."));
        assertEquals(List.of("Hello,", "world!"), ErrantTokenSupport.tokenize("  Hello,   world!  "));
        assertTrue(ErrantTokenSupport.tokenize("   ").isEmpty());
    }

    @Test
    void parseOpExtractsRMU() {
        assertEquals("R", ErrantAnnotatorClient.parseOp("R:VERB:SVA"));
        assertEquals("M", ErrantAnnotatorClient.parseOp("M:DET"));
        assertEquals("U", ErrantAnnotatorClient.parseOp("U:OTHER"));
        assertNull(ErrantAnnotatorClient.parseOp("noop"));
        assertNull(ErrantAnnotatorClient.parseOp("UNKNOWN"));
        assertNull(ErrantAnnotatorClient.parseOp(null));
    }

    @Test
    void offsetsValidAcceptsEmpty() {
        assertTrue(ErrantEditAnnotationService.offsetsValid(List.of(), List.of(), List.of()));
    }

    @Test
    void offsetsValidRejectsOutOfRange() {
        SentenceEditDto ok = SentenceEditDto.builder()
                .op("R").oStart(1).oEnd(2).oStr("are").cStart(1).cEnd(2).cStr("is").build();
        assertTrue(ErrantEditAnnotationService.offsetsValid(
                List.of(ok), List.of("This", "are"), List.of("This", "is")));

        SentenceEditDto bad = SentenceEditDto.builder()
                .op("R").oStart(1).oEnd(5).oStr("x").cStart(0).cEnd(1).cStr("y").build();
        assertFalse(ErrantEditAnnotationService.offsetsValid(
                List.of(bad), List.of("This", "are"), List.of("This", "is")));
    }

    @Test
    void codecRoundTripMergesIntoItems() {
        ErrantEditAnnotationsCodec codec = new ErrantEditAnnotationsCodec(new ObjectMapper());
        AnalysisItemDto item = AnalysisItemDto.builder()
                .sentenceId(1L)
                .originalSentence("This are a test.")
                .suggestion("This is a test.")
                .originalTokens(List.of("This", "are", "a", "test."))
                .correctedTokens(List.of("This", "is", "a", "test."))
                .edits(List.of(SentenceEditDto.builder()
                        .op("R").oStart(1).oEnd(2).oStr("are").cStart(1).cEnd(2).cStr("is").build()))
                .build();

        String json = codec.serializeFromItems(List.of(item));
        assertTrue(json != null && json.contains("\"op\":\"R\""));

        AnalysisItemDto reloaded = AnalysisItemDto.builder()
                .sentenceId(1L)
                .originalSentence("This are a test.")
                .suggestion("This is a test.")
                .build();
        codec.mergeIntoItems(List.of(reloaded), json);
        assertEquals(List.of("This", "are", "a", "test."), reloaded.getOriginalTokens());
        assertEquals(1, reloaded.getEdits().size());
        assertEquals("R", reloaded.getEdits().getFirst().getOp());
        assertEquals(1, reloaded.getEdits().getFirst().getOStart());
    }

    @Test
    void jacksonWritesCamelCaseOffsetFieldsNotLowercaseOstart() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SentenceEditDto edit = SentenceEditDto.builder()
                .op("M").oStart(2).oEnd(2).oStr("").cStart(2).cEnd(4).cStr("we are").build();

        String json = mapper.writeValueAsString(edit);
        JsonNode node = mapper.readTree(json);

        assertTrue(node.has("oStart"), () -> "expected oStart in " + json);
        assertFalse(node.has("ostart"), () -> "legacy ostart must not be serialized: " + json);
        assertTrue(node.has("oEnd"));
        assertTrue(node.has("cStart"));
        assertTrue(node.has("cEnd"));
        assertEquals(2, node.get("oStart").asInt());
        assertEquals(4, node.get("cEnd").asInt());
    }

    @Test
    void jacksonReadsLegacyLowercaseOstartFromStoredJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String legacy = """
                {"op":"M","ostart":2,"oend":2,"ostr":"","cstart":2,"cend":4,"cstr":"we are"}
                """;

        SentenceEditDto edit = mapper.readValue(legacy, SentenceEditDto.class);

        assertEquals("M", edit.getOp());
        assertEquals(2, edit.getOStart());
        assertEquals(2, edit.getOEnd());
        assertEquals(2, edit.getCStart());
        assertEquals(4, edit.getCEnd());
        assertEquals("we are", edit.getCStr());
    }
}
