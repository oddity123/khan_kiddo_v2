package com.khankiddo.learning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 行动卡例句影子跟读：sherpa-onnx 离线识别打分 + edge-tts 原声。
 * 设计见 docs/todo/shadowing/2026-10-06-design.md。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.shadowing")
public class ShadowingProperties {

    /** 识别打分总开关；关闭时不加载模型 */
    private boolean enabled = false;

    /** 灰度：仅管理员可用 */
    private boolean adminOnly = false;

    /** sherpa-onnx Zipformer 模型目录（含 int8 encoder/decoder/joiner 与 tokens.txt） */
    private String modelDir = "";

    private int numThreads = 1;

    /** 同时识别的句数 */
    private int maxConcurrent = 1;

    /** 识别排队上限；正在识别 + 排队满时直接拒绝 */
    private int maxQueue = 2;

    private Duration queueTimeout = Duration.ofSeconds(10);

    private int maxAudioSeconds = 20;

    private int passScore = 60;

    private Tts tts = new Tts();

    @Data
    public static class Tts {

        private boolean enabled = false;

        /** edge-tts 小服务根地址，不含尾斜杠 */
        private String baseUrl = "http://127.0.0.1:8001";

        private String voice = "en-US-AriaNeural";

        private String cacheDir = "./data/tts-cache";

        private Duration connectTimeout = Duration.ofSeconds(2);

        private Duration readTimeout = Duration.ofSeconds(15);
    }
}
