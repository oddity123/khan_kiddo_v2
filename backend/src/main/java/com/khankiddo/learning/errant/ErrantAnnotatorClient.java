package com.khankiddo.learning.errant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.khankiddo.learning.config.ErrantProperties;
import com.khankiddo.learning.dto.conversation.SentenceEditDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERRANT {@code POST /v1/annotate} 客户端。失败仅打日志，由调用方降级为无 edits。
 */
@Slf4j
@Component
public class ErrantAnnotatorClient {

    private final ErrantProperties properties;
    private final RestClient restClient;

    public ErrantAnnotatorClient(ErrantProperties properties) {
        this.properties = properties;
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(properties.getConnectTimeout())
                        .withReadTimeout(properties.getReadTimeout()));
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 批量标注。key 为调用方 id（通常 sentenceId 字符串）。
     * 返回 map 仅包含成功且解析出 R/M/U 的条目；失败项不出现。
     */
    public Map<String, List<SentenceEditDto>> annotateBatch(List<AnnotatePair> pairs) {
        Map<String, List<SentenceEditDto>> empty = Map.of();
        if (!properties.isEnabled() || CollectionUtils.isEmpty(pairs)) {
            return empty;
        }
        List<Map<String, Object>> sentences = new ArrayList<>();
        for (AnnotatePair pair : pairs) {
            if (!StringUtils.hasText(pair.id())
                    || !StringUtils.hasText(pair.original())
                    || !StringUtils.hasText(pair.corrected())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pair.id());
            row.put("original", pair.original());
            row.put("corrected", pair.corrected());
            sentences.add(row);
        }
        if (sentences.isEmpty()) {
            return empty;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sentences", sentences);
        // 调用方已按 ErrantTokenSupport 分词并用空格拼接，关闭 spaCy 再分词以保持下标一致
        body.put("tokenise", false);

        try {
            AnnotateResponse response = restClient.post()
                    .uri("/v1/annotate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AnnotateResponse.class);
            if (response == null || CollectionUtils.isEmpty(response.results())) {
                return empty;
            }
            Map<String, List<SentenceEditDto>> out = new LinkedHashMap<>();
            for (SentenceResult result : response.results()) {
                if (result == null || !StringUtils.hasText(result.id()) || StringUtils.hasText(result.error())) {
                    if (result != null && StringUtils.hasText(result.error())) {
                        log.warn("ERRANT per-item error id={}: {}", result.id(), result.error());
                    }
                    continue;
                }
                List<SentenceEditDto> edits = toEdits(result.edits());
                if (!edits.isEmpty()) {
                    out.put(result.id(), edits);
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("ERRANT annotate failed (soft-fail): {}", ex.toString());
            return empty;
        }
    }

    static List<SentenceEditDto> toEdits(List<EditOut> rawEdits) {
        if (CollectionUtils.isEmpty(rawEdits)) {
            return List.of();
        }
        List<SentenceEditDto> edits = new ArrayList<>();
        for (EditOut raw : rawEdits) {
            if (raw == null) {
                continue;
            }
            String op = parseOp(raw.type());
            if (op == null) {
                continue;
            }
            edits.add(SentenceEditDto.builder()
                    .op(op)
                    .oStart(raw.oStart())
                    .oEnd(raw.oEnd())
                    .oStr(raw.oStr() != null ? raw.oStr() : "")
                    .cStart(raw.cStart())
                    .cEnd(raw.cEnd())
                    .cStr(raw.cStr() != null ? raw.cStr() : "")
                    .build());
        }
        return edits;
    }

    /**
     * 从 ERRANT type（如 {@code R:VERB:SVA}）取出操作前缀；无法识别则返回 null。
     */
    static String parseOp(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String trimmed = type.trim();
        if ("noop".equalsIgnoreCase(trimmed)) {
            return null;
        }
        int colon = trimmed.indexOf(':');
        String op = colon > 0 ? trimmed.substring(0, colon) : trimmed;
        return switch (op) {
            case "R", "M", "U" -> op;
            default -> null;
        };
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://127.0.0.1:8000";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record AnnotatePair(String id, String original, String corrected) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AnnotateResponse(List<SentenceResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SentenceResult(
            String id,
            String original,
            String corrected,
            List<EditOut> edits,
            String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EditOut(
            @JsonProperty("o_start") int oStart,
            @JsonProperty("o_end") int oEnd,
            @JsonProperty("o_str") String oStr,
            @JsonProperty("c_start") int cStart,
            @JsonProperty("c_end") int cEnd,
            @JsonProperty("c_str") String cStr,
            String type) {
    }
}
