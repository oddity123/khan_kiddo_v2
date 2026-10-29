package com.khankiddo.learning.voice;

import com.khankiddo.learning.voice.tool.VoiceRealtimeToolContext;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolHandler;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按工具名分发；未知工具返回结构化 error，避免模型空等。
 */
@Slf4j
@Component
public class VoiceRealtimeToolGateway {

    private final Map<String, VoiceRealtimeToolHandler> handlersByName;

    public VoiceRealtimeToolGateway(List<VoiceRealtimeToolHandler> handlers) {
        Map<String, VoiceRealtimeToolHandler> map = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(handlers)) {
            for (VoiceRealtimeToolHandler handler : handlers) {
                if (handler != null && StringUtils.hasText(handler.name())) {
                    map.put(handler.name(), handler);
                }
            }
        }
        this.handlersByName = Map.copyOf(map);
    }

    public record ToolExecution(String name, String outputJson, boolean known, VoiceLearningEvent learningEvent) {

        public ToolExecution(String name, String outputJson, boolean known) {
            this(name, outputJson, known, null);
        }
    }

    public ToolExecution execute(VoiceRealtimeToolContext context, String name, String argumentsJson) {
        VoiceRealtimeToolHandler handler = handlersByName.get(name);
        if (handler == null) {
            log.warn("unknown voice realtime tool name={} practiceSessionId={}",
                    name, context.practiceSessionId());
            return new ToolExecution(name,
                    "{\"ok\":false,\"error\":\"unknown_tool:" + escape(name) + "\"}",
                    false);
        }
        String args = StringUtils.hasText(argumentsJson) ? argumentsJson : "{}";
        VoiceRealtimeToolResult result = handler.execute(context, args);
        return new ToolExecution(name, result.outputJson(), true, result.learningEvent());
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("\"", "'");
    }
}
