package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.util.StringUtils;

public enum HabitUnit {

    FAMILY("family"),
    LEAF("leaf"),
    CHANNEL("channel");

    private final String jsonValue;

    HabitUnit(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static HabitUnit fromJson(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("habitUnit is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "family" -> FAMILY;
            case "leaf" -> LEAF;
            case "channel" -> CHANNEL;
            default -> throw new IllegalArgumentException("Unknown habitUnit: " + value);
        };
    }
}
