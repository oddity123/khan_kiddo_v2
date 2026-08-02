package com.khankiddo.learning.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.util.StringUtils;

public enum CardKind {

    GRAMMAR("grammar"),
    FLUENCY_STRATEGY("fluency_strategy"),
    LEXICAL_UPGRADE("lexical_upgrade"),
    CHINESE_BYPASS("chinese_bypass");

    private final String jsonValue;

    CardKind(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static CardKind fromJson(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("cardKind is required");
        }
        return switch (value.trim().toLowerCase()) {
            case "grammar" -> GRAMMAR;
            case "fluency_strategy" -> FLUENCY_STRATEGY;
            case "lexical_upgrade" -> LEXICAL_UPGRADE;
            case "chinese_bypass" -> CHINESE_BYPASS;
            default -> throw new IllegalArgumentException("Unknown cardKind: " + value);
        };
    }
}
