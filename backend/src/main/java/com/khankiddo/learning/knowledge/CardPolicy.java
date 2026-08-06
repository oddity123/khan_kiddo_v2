package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.util.StringUtils;

public enum CardPolicy {

    NORMAL("normal"),
    RARE("rare"),
    CHANNEL("channel");

    private final String jsonValue;

    CardPolicy(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static CardPolicy fromJson(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("cardPolicy is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "normal" -> NORMAL;
            case "rare" -> RARE;
            case "channel" -> CHANNEL;
            default -> throw new IllegalArgumentException("Unknown cardPolicy: " + value);
        };
    }
}
