package com.khankiddo.learning.voice.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;
import com.khankiddo.learning.voice.VoiceLearningEvent;
import com.khankiddo.learning.voice.VoicePracticeMaterialAccess;
import com.khankiddo.learning.voice.VoicePracticeMaterialSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 学习类工具共用：入参解析、截断、本场快照范围校验、错误 JSON。
 */
@Component
@RequiredArgsConstructor
public class VoiceLearningToolSupport {

    public static final int MAX_UTTERANCE = 500;
    public static final int MAX_REASON = 300;
    public static final int MAX_NOTE = 500;

    private final ObjectMapper objectMapper;
    private final VoicePracticeMaterialAccess materialAccess;
    private final GrowthCardStore growthCardStore;

    public ObjectMapper mapper() {
        return objectMapper;
    }

    public JsonNode parseArgs(String argumentsJson) {
        try {
            JsonNode node = objectMapper.readTree(StringUtils.hasText(argumentsJson) ? argumentsJson : "{}");
            if (node == null || !node.isObject()) {
                throw new VoiceToolRejectedException("invalid_arguments", "arguments must be a JSON object");
            }
            return node;
        } catch (VoiceToolRejectedException e) {
            throw e;
        } catch (Exception e) {
            throw new VoiceToolRejectedException("invalid_arguments", "arguments are not valid JSON");
        }
    }

    /** 读字符串字段并 trim；缺失 / 非文本返回空串。 */
    public static String text(JsonNode args, String field) {
        JsonNode v = args.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return "";
        }
        return v.asText("").trim();
    }

    public static String clip(String raw, int max) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    public VoicePracticeMaterialSnapshot snapshot(VoiceRealtimeToolContext ctx) {
        return materialAccess.requireSnapshot(ctx.userId(), ctx.practiceSessionId());
    }

    public GrowthCard requireCardInSnapshot(VoiceRealtimeToolContext ctx, String cardId) {
        if (!StringUtils.hasText(cardId)) {
            throw new VoiceToolRejectedException("card_id_required", "cardId is required");
        }
        if (!snapshot(ctx).cardIds().contains(cardId)) {
            throw new VoiceToolRejectedException("card_not_in_session", "card is not part of this practice session");
        }
        return growthCardStore.findOwned(cardId, ctx.userId())
                .orElseThrow(() -> new VoiceToolRejectedException("card_not_found", "card no longer exists"));
    }

    public String requireAnalysisInSnapshot(VoiceRealtimeToolContext ctx, String analysisId) {
        if (!snapshot(ctx).analysisIds().contains(analysisId)) {
            throw new VoiceToolRejectedException(
                    "analysis_not_in_session", "analysis is not part of this practice session");
        }
        return analysisId;
    }

    /** 本场证据行骨架：归属与时间由服务端写入，不信任模型。 */
    public VoicePracticeEvent newEvent(VoiceRealtimeToolContext ctx, String eventType) {
        return VoicePracticeEvent.builder()
                .practiceSessionId(ctx.practiceSessionId())
                .userId(ctx.userId())
                .eventType(eventType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public VoiceLearningEvent panelEvent(String type, Object dto) {
        return new VoiceLearningEvent(type, objectMapper.valueToTree(dto));
    }

    public String errorJson(String code, String message) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("ok", false);
        root.put("error", code);
        if (StringUtils.hasText(message)) {
            root.put("message", message);
        }
        return write(root);
    }

    public String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"serialize_failed\"}";
        }
    }
}
