package com.khankiddo.learning.controller;

import com.khankiddo.learning.config.VoiceRealtimeProperties;
import com.khankiddo.learning.dto.voice.VoiceRealtimeConfigResponse;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 豆包端到端实时语音（全双工）配置探测。
 * 实际音频事件经 {@code /api/voice/realtime/ws} 由后端代理至 openspeech，密钥不落前端。
 */
@RestController
@RequestMapping("/api/voice/realtime")
@RequiredArgsConstructor
public class VoiceRealtimeController {

    public static final String WS_PATH = "/api/voice/realtime/ws";

    private final VoiceRealtimeProperties properties;

    @GetMapping("/config")
    public VoiceRealtimeConfigResponse config() {
        SecurityUtils.requireUserId();
        if (!properties.isConfigured()) {
            return VoiceRealtimeConfigResponse.builder()
                    .configured(false)
                    .wsPath(WS_PATH)
                    .model(properties.getModel())
                    .voice(properties.getVoice())
                    .defaultInstructions(properties.getDefaultInstructions())
                    .inputSampleRate(16_000)
                    .outputSampleRate(24_000)
                    .chunkMs(20)
                    .message("未配置豆包语音实时对话密钥（DOUBAO_SPEECH_API_KEY）。"
                            + "请在火山引擎「豆包语音」控制台创建 API Key 后写入 .env，与方舟 DOUBAO_API_KEY 不是同一把钥匙。")
                    .build();
        }
        return VoiceRealtimeConfigResponse.builder()
                .configured(true)
                .wsPath(WS_PATH)
                .model(properties.getModel())
                .voice(properties.getVoice())
                .defaultInstructions(properties.getDefaultInstructions())
                .inputSampleRate(16_000)
                .outputSampleRate(24_000)
                .chunkMs(20)
                .message(null)
                .build();
    }
}
