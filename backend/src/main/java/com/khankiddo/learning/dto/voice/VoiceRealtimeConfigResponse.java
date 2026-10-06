package com.khankiddo.learning.dto.voice;

import lombok.Builder;
import lombok.Value;

/**
 * 前端启动实时语音前的配置探测（不含密钥）。
 */
@Value
@Builder
public class VoiceRealtimeConfigResponse {

    boolean configured;

    /** 相对当前站点的 WebSocket 路径（需附带 {@code access_token}） */
    String wsPath;

    String model;

    String voice;

    String defaultInstructions;

    /** 输入采样率（Hz），文档仅支持 16000 */
    int inputSampleRate;

    /** 输出采样率（Hz），文档仅支持 24000 */
    int outputSampleRate;

    /** 建议分包时长（毫秒） */
    int chunkMs;

    /** 缺 Key 时的友好提示；已配置时为 null */
    String message;
}
