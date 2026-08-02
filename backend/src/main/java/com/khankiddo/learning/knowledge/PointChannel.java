package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.util.StringUtils;

public enum PointChannel {

    RULE("rule"),
    FLUENCY("fluency"),
    LEXICAL("lexical"),
    CHINESE("chinese");

    private final String jsonValue;

    PointChannel(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static PointChannel fromJson(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("channel is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "rule" -> RULE;
            case "fluency" -> FLUENCY;
            case "lexical" -> LEXICAL;
            case "chinese" -> CHINESE;
            default -> throw new IllegalArgumentException("Unknown channel: " + value);
        };
    }
}
