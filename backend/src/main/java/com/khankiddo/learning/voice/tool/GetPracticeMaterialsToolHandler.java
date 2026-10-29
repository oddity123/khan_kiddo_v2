package com.khankiddo.learning.voice.tool;

import com.khankiddo.learning.voice.VoicePracticeMaterialAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 在本场勾选范围内返回成长卡与分析摘要，供口语教练出题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetPracticeMaterialsToolHandler implements VoiceRealtimeToolHandler {

    public static final String NAME = "get_practice_materials";

    private final VoicePracticeMaterialAccess materialAccess;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public VoiceRealtimeToolResult execute(VoiceRealtimeToolContext context, String argumentsJson) {
        try {
            return VoiceRealtimeToolResult.of(
                    materialAccess.buildMaterialsToolJson(context.userId(), context.practiceSessionId()));
        } catch (Exception e) {
            log.warn("get_practice_materials failed practiceSessionId={}: {}",
                    context.practiceSessionId(), e.toString());
            return VoiceRealtimeToolResult.of("{\"ok\":false,\"error\":\"materials_unavailable\"}");
        }
    }
}
