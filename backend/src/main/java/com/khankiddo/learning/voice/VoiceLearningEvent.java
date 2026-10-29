package com.khankiddo.learning.voice;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具成功后推给浏览器面板的类型化事件（type 如 {@code proxy.learning.focus}）。
 */
public record VoiceLearningEvent(String type, JsonNode payload) {
}
