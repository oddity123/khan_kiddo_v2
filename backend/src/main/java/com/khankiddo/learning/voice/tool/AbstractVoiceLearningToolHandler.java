package com.khankiddo.learning.voice.tool;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 学习类工具模板：统一解析入参、把校验失败转成结构化 error（模型可读），未预期异常不外抛。
 */
@Slf4j
abstract class AbstractVoiceLearningToolHandler implements VoiceRealtimeToolHandler {

    private final VoiceLearningToolSupport support;

    AbstractVoiceLearningToolHandler(VoiceLearningToolSupport support) {
        this.support = support;
    }

    @Override
    public final VoiceRealtimeToolResult execute(VoiceRealtimeToolContext context, String argumentsJson) {
        try {
            return doExecute(context, support.parseArgs(argumentsJson));
        } catch (VoiceToolRejectedException e) {
            return VoiceRealtimeToolResult.of(support.errorJson(e.code(), e.getMessage()));
        } catch (Exception e) {
            log.warn("voice tool {} failed practiceSessionId={}: {}",
                    name(), context.practiceSessionId(), e.toString());
            return VoiceRealtimeToolResult.of(support.errorJson("tool_failed", null));
        }
    }

    protected abstract VoiceRealtimeToolResult doExecute(VoiceRealtimeToolContext context, JsonNode args);
}
