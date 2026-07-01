package com.khankiddo.learning.rag.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.dto.rag.RagStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseStreamHelper {

    private final ObjectMapper objectMapper;

    public void sendEvent(SseEmitter emitter, AtomicBoolean finished, RagStreamEvent event) {
        if (finished.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(event)));
            if (RagStreamEvent.STATUS_ERROR.equals(event.getStatus())) {
                finished.set(true);
                emitter.complete();
            }
        } catch (IOException ex) {
            log.debug("SSE 客户端已断开: {}", ex.getMessage());
            finished.set(true);
            emitter.completeWithError(ex);
        }
    }

    public void complete(SseEmitter emitter, AtomicBoolean finished) {
        if (!finished.get()) {
            finished.set(true);
            emitter.complete();
        }
    }

    public void fail(SseEmitter emitter, AtomicBoolean finished, String message) {
        sendEvent(emitter, finished, RagStreamEvent.error(
                StringUtils.hasText(message) ? message : "流式回答失败"));
    }
}
