package com.khankiddo.learning.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagStreamEvent {

    public static final String STATUS_TOKEN = "token";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_ERROR = "error";

    private String status;
    private String token;
    private String message;

    public static RagStreamEvent token(String token) {
        return RagStreamEvent.builder().status(STATUS_TOKEN).token(token).build();
    }

    public static RagStreamEvent done() {
        return RagStreamEvent.builder().status(STATUS_DONE).build();
    }

    public static RagStreamEvent error(String message) {
        return RagStreamEvent.builder().status(STATUS_ERROR).message(message).build();
    }
}
