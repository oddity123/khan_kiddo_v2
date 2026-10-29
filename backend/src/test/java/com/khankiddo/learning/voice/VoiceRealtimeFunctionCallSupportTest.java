package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceRealtimeFunctionCallSupportTest {

    private final VoiceRealtimeFunctionCallSupport support =
            new VoiceRealtimeFunctionCallSupport(new ObjectMapper());

    @Test
    void parseFunctionCalls_readsItems() throws Exception {
        String payload = """
                {"type":"response.function_call_arguments.done","items":[
                  {"call_id":"c1","name":"ping_practice_lab","arguments":"{}"},
                  {"call_id":"c2","name":"other","arguments":"{\\"x\\":1}"}
                ]}
                """;
        List<VoiceRealtimeFunctionCallSupport.FunctionCall> calls = support.parseFunctionCalls(payload);
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).callId()).isEqualTo("c1");
        assertThat(calls.get(0).name()).isEqualTo("ping_practice_lab");
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{}");
        assertThat(calls.get(1).argumentsJson()).contains("\"x\"");
    }

    @Test
    void parseFunctionCalls_ignoresWrongTypeAndBadItems() throws Exception {
        assertThat(support.parseFunctionCalls("{\"type\":\"error\"}")).isEmpty();
        assertThat(support.parseFunctionCalls("""
                {"type":"response.function_call_arguments.done","items":[
                  {"name":"no_id"},
                  {"call_id":"c1","name":""},
                  null
                ]}
                """)).isEmpty();
    }

    @Test
    void looksLikeFunctionCallArgumentsDone_detectsType() {
        assertThat(support.looksLikeFunctionCallArgumentsDone(
                "{\"type\":\"response.function_call_arguments.done\"}")).isTrue();
        assertThat(support.looksLikeFunctionCallArgumentsDone(
                "{ \"type\": \"response.function_call_arguments.done\" }")).isTrue();
        assertThat(support.looksLikeFunctionCallArgumentsDone(
                "{\"type\":\"session.created\"}")).isFalse();
    }

    @Test
    void buildToolResultPayload_matchesDoubaoShapeWithMessageType() throws Exception {
        String json = support.buildToolResultPayload("call-1", "{\"ok\":true}");
        var root = new ObjectMapper().readTree(json);
        assertThat(root.path("type").asText()).isEqualTo("conversation.item.create");
        var item = root.path("items").path(0);
        assertThat(item.path("type").asText()).isEqualTo("message");
        assertThat(item.path("call_id").asText()).isEqualTo("call-1");
        assertThat(item.path("role").asText()).isEqualTo("tool");
        assertThat(item.path("content").path(0).path("type").asText()).isEqualTo("input_text");
        assertThat(item.path("content").path(0).path("text").asText()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void buildLearningToolEvent_wrapsSummary() throws Exception {
        String json = support.buildLearningToolEvent("call-1", "ping_practice_lab", "replied", "{\"ok\":true}");
        assertThat(json).contains("\"type\":\"proxy.learning.tool\"");
        assertThat(json).contains("\"callId\":\"call-1\"");
        assertThat(json).contains("\"name\":\"ping_practice_lab\"");
        assertThat(json).contains("\"phase\":\"replied\"");
    }

    @Test
    void buildLearningPanelEvent_wrapsTypeCallIdAndPayload() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        VoiceLearningEvent event = new VoiceLearningEvent(
                VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_FOCUS,
                mapper.readTree("{\"cardId\":\"c1\",\"front\":\"f\"}"));
        var root = mapper.readTree(support.buildLearningPanelEvent("call-9", event));
        assertThat(root.path("type").asText()).isEqualTo("proxy.learning.focus");
        assertThat(root.path("callId").asText()).isEqualTo("call-9");
        assertThat(root.path("payload").path("cardId").asText()).isEqualTo("c1");
    }

    @Test
    void learningPanelEventTypesAreStable() {
        assertThat(VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_FOCUS).isEqualTo("proxy.learning.focus");
        assertThat(VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_ATTEMPT).isEqualTo("proxy.learning.attempt");
        assertThat(VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_NOTE).isEqualTo("proxy.learning.note");
    }
}
