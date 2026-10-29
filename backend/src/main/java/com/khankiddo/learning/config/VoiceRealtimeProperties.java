package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 豆包语音「端到端实时语音 / 全双工」配置（openspeech，与方舟 {@code DOUBAO_API_KEY} 分离）。
 *
 * @see <a href="https://docs.volcengine.com/docs/DoubaoVoice/endtoend-realtime-voice-full-duplex-version">官方文档</a>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.voice.realtime")
public class VoiceRealtimeProperties {

    public static final String DEFAULT_WS_URL =
            "wss://openspeech.bytedance.com/api/v3/duplex/realtime/dialogue";
    public static final String DEFAULT_MODEL = "1.2.6.1";
    public static final String DEFAULT_VOICE = "zh_female_vv_jupiter_bigtts";
    public static final String DEFAULT_RESOURCE_ID = "volc.speech.dialog";
    /** 输入 PCM 采样率（Hz）；openspeech 全双工文档仅支持 16000 */
    public static final int DEFAULT_INPUT_SAMPLE_RATE = 16_000;
    /** 输出 PCM 采样率（Hz）；openspeech 全双工文档仅支持 24000 */
    public static final int DEFAULT_OUTPUT_SAMPLE_RATE = 24_000;
    /** 建议上行分包时长（毫秒） */
    public static final int DEFAULT_CHUNK_MS = 20;

    /** 新版控制台 API Key（请求头 {@code X-Api-Key}）；优先于旧版 AppId/AccessKey */
    private String apiKey = "";

    private String wsUrl = DEFAULT_WS_URL;

    /** 全双工固定 model 值 */
    private String model = DEFAULT_MODEL;

    private String voice = DEFAULT_VOICE;

    /** 旧版控制台 AppId（{@code X-Api-App-Id}），仅在未配置 apiKey 时使用 */
    private String appId = "";

    /** 旧版 Access Token（{@code X-Api-Access-Key}） */
    private String accessKey = "";

    /** 旧版资源 ID，对话场景固定 {@code volc.speech.dialog} */
    private String resourceId = DEFAULT_RESOURCE_ID;

    /** 输入采样率（Hz），文档仅支持 16000 */
    private int inputSampleRate = DEFAULT_INPUT_SAMPLE_RATE;

    /** 输出采样率（Hz），文档仅支持 24000 */
    private int outputSampleRate = DEFAULT_OUTPUT_SAMPLE_RATE;

    /** 建议分包时长（毫秒） */
    private int chunkMs = DEFAULT_CHUNK_MS;

    /**
     * 默认教练人设（材料驱动）；前端 session.create 可用本场自定义提示词覆盖。
     * 工具调用协议（先取材料、逐卡聚焦、记录证据）随工具声明在前端 voiceRealtimeFunctionCall.ts 追加。
     */
    private String defaultInstructions = """
            You are a warm, patient English speaking coach for Chinese learners.
            Your job is to help the learner actually say their own target expressions out loud,
            drawn from the practice materials selected for this session.
            Keep each turn short (1-3 sentences) and ask one question at a time, then wait.
            Create a small natural situation that makes the learner produce the target themselves;
            do not read out the answer first. Prefer natural spoken English.
            Give a hint only after the learner struggles, and correct only errors that block understanding.
            Be honest: only count it as success when the learner said it correctly without your help.
            """;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) || (StringUtils.hasText(appId) && StringUtils.hasText(accessKey));
    }

    public boolean usesNewApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
