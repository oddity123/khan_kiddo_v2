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

    /**
     * 默认系统提示词；前端可在 session.create 中覆盖。
     */
    private String defaultInstructions = """
            You are a warm English speaking coach for Chinese learners.
            Keep each turn short (1–3 sentences), ask one question at a time,
            and wait for the learner to speak. Prefer natural spoken English.
            Gently correct only errors that block understanding.
            """;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) || (StringUtils.hasText(appId) && StringUtils.hasText(accessKey));
    }

    public boolean usesNewApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
