package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 豆包全双工 Function Calling：解析下行、拼装 tool result / 学习面板事件。
 */
@Component
@RequiredArgsConstructor
public class VoiceRealtimeFunctionCallSupport {

    public static final String EVENT_FUNCTION_CALL_ARGUMENTS_DONE = "response.function_call_arguments.done";
    public static final String EVENT_LEARNING_TOOL = "proxy.learning.tool";
    public static final String EVENT_LEARNING_FOCUS = "proxy.learning.focus";
    public static final String EVENT_LEARNING_ATTEMPT = "proxy.learning.attempt";
    public static final String EVENT_LEARNING_NOTE = "proxy.learning.note";

    private final ObjectMapper objectMapper;

    public record FunctionCall(String callId, String name, String argumentsJson) {
    }

    public boolean looksLikeFunctionCallArgumentsDone(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        return payload.contains("\"type\":\"" + EVENT_FUNCTION_CALL_ARGUMENTS_DONE + "\"")
                || payload.contains("\"type\": \"" + EVENT_FUNCTION_CALL_ARGUMENTS_DONE + "\"");
    }

    public List<FunctionCall> parseFunctionCalls(String payload) throws Exception {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(payload);
        if (!EVENT_FUNCTION_CALL_ARGUMENTS_DONE.equals(text(root, "type"))) {
            return List.of();
        }
        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        List<FunctionCall> out = new ArrayList<>();
        for (JsonNode item : items) {
            if (item == null || item.isNull() || !item.isObject()) {
                continue;
            }
            String callId = text(item, "call_id");
            String name = text(item, "name");
            if (!StringUtils.hasText(callId) || !StringUtils.hasText(name)) {
                continue;
            }
            String args = text(item, "arguments");
            if (!StringUtils.hasText(args)) {
                args = "{}";
            }
            out.add(new FunctionCall(callId.trim(), name.trim(), args));
        }
        return Collections.unmodifiableList(out);
    }

    public String buildToolResultPayload(String callId, String outputText) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "conversation.item.create");
        ArrayNode items = root.putArray("items");
        ObjectNode item = items.addObject();
        item.put("type", "message");
        item.put("call_id", callId);
        item.put("role", "tool");
        ArrayNode content = item.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "input_text");
        block.put("text", outputText);
        return objectMapper.writeValueAsString(root);
    }

    public String buildLearningToolEvent(
            String callId, String name, String phase, String detail) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", EVENT_LEARNING_TOOL);
        root.put("callId", callId);
        root.put("name", name);
        root.put("phase", phase);
        root.put("detail", detail == null ? "" : detail);
        return objectMapper.writeValueAsString(root);
    }

    /**
     * 面板类型化事件：{@code {type, callId, payload}}；payload 形状与 REST hydrate 的 DTO 一致。
     */
    public String buildLearningPanelEvent(String callId, VoiceLearningEvent event) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", event.type());
        root.put("callId", callId == null ? "" : callId);
        root.set("payload", event.payload());
        return objectMapper.writeValueAsString(root);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        return v.asText("");
    }
}
