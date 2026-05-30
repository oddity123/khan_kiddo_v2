package com.khankiddo.learning.model.enums;

public enum ErrorLevel {

    FATAL(0),
    BASIC(1),
    NATURAL(2),
    STYLE(3);

    private final int order;

    ErrorLevel(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
