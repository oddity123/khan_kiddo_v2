package com.khankiddo.learning.dto.langchain4j;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Langchain4jLearningStreamEvent {

    public static final String STATUS_TOKEN = "TOKEN";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_ERROR = "ERROR";

    String status;
    String token;
    String message;

    public static Langchain4jLearningStreamEvent token(String token) {
        return Langchain4jLearningStreamEvent.builder()
                .status(STATUS_TOKEN)
                .token(token)
                .build();
    }

    public static Langchain4jLearningStreamEvent done() {
        return Langchain4jLearningStreamEvent.builder()
                .status(STATUS_DONE)
                .build();
    }

    public static Langchain4jLearningStreamEvent error(String message) {
        return Langchain4jLearningStreamEvent.builder()
                .status(STATUS_ERROR)
                .message(message)
                .build();
    }
}
