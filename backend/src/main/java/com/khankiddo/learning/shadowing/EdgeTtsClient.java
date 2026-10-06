package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.config.ShadowingProperties;
import com.khankiddo.learning.util.TextSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * services/edge-tts {@code POST /v1/tts} 客户端。失败仅打日志，由调用方降级为浏览器朗读。
 */
@Slf4j
@Component
public class EdgeTtsClient {

    private final ShadowingProperties.Tts properties;
    private final RestClient restClient;

    public EdgeTtsClient(ShadowingProperties properties) {
        this.properties = properties.getTts();
        this.restClient = RestClient.builder()
                .baseUrl(TextSupport.trimTrailingSlashes(this.properties.getBaseUrl(), "http://127.0.0.1:8001"))
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(this.properties.getConnectTimeout())
                        .withReadTimeout(this.properties.getReadTimeout())))
                .build();
    }

    public Optional<byte[]> synthesize(String text, String voice) {
        try {
            byte[] audio = restClient.post()
                    .uri("/v1/tts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/mpeg"))
                    .body(Map.of("text", text, "voice", voice))
                    .retrieve()
                    .body(byte[].class);
            if (ObjectUtils.isEmpty(audio)) {
                log.warn("edge-tts 返回空音频 voice={}", voice);
                return Optional.empty();
            }
            return Optional.of(audio);
        } catch (Exception ex) {
            log.warn("edge-tts 合成失败（降级为浏览器朗读）: {}", ex.toString());
            return Optional.empty();
        }
    }
}
