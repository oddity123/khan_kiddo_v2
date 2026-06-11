package com.khankiddo.learning.service.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.ai.langchain4j.Langchain4jLearningAi;
import com.khankiddo.learning.config.condition.OnQwenConfiguredCondition;
import com.khankiddo.learning.dto.langchain4j.Langchain4jLearningStreamEvent;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.llm.LlmModelCatalog;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@Conditional(OnQwenConfiguredCondition.class)
@RequiredArgsConstructor
public class Langchain4jLearningStreamService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final String QWEN_PLUS_MODEL_ID = "qwen-plus";

    private final Langchain4jLearningAi learningAi;
    private final LlmModelCatalog modelCatalog;
    private final ObjectMapper objectMapper;

    public SseEmitter chatStream(String message) {
        assertQwenConfigured();
        if (!StringUtils.hasText(message)) {
            throw new BadRequestException("请输入问题");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);

        emitter.onCompletion(() -> finished.set(true));
        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> finished.set(true));

        Thread.startVirtualThread(() -> {
            try {
                learningAi.chat(message.trim())
                        .onRetrieved(this::logRetrievedContents)
                        .onPartialResponse(token -> sendEvent(emitter, finished, Langchain4jLearningStreamEvent.token(token)))
                        .onCompleteResponse(response -> {
                            sendEvent(emitter, finished, Langchain4jLearningStreamEvent.done());
                            if (!finished.get()) {
                                finished.set(true);
                                emitter.complete();
                            }
                        })
                        .onError(error -> {
                            log.error("LangChain4j 学习流式回答失败", error);
                            sendEvent(emitter, finished, Langchain4jLearningStreamEvent.error(
                                    StringUtils.hasText(error.getMessage()) ? error.getMessage() : "流式回答失败"));
                            if (!finished.get()) {
                                finished.set(true);
                                emitter.complete();
                            }
                        })
                        .start();
            } catch (Exception ex) {
                log.error("LangChain4j 学习流式任务启动失败", ex);
                sendEvent(emitter, finished, Langchain4jLearningStreamEvent.error(
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "流式回答失败"));
                if (!finished.get()) {
                    finished.set(true);
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    private void logRetrievedContents(java.util.List<Content> contents) {
        if (CollectionUtils.isEmpty(contents)) {
            log.info("LangChain for Java RAG: 未检索到满足 minScore 的文档片段");
            return;
        }
        for (Content content : contents) {
            var segment = content.textSegment();
            String preview = segment.text();
            if (StringUtils.hasText(preview) && preview.length() > 120) {
                preview = preview.substring(0, 120) + "…";
            }
            log.info(
                    "LangChain for Java RAG: 命中 file={} type={} table={} section={} preview={}",
                    segment.metadata().getString("file_name"),
                    segment.metadata().getString("document_type"),
                    segment.metadata().getString("table_name"),
                    segment.metadata().getString("section"),
                    preview);
        }
    }

    private void assertQwenConfigured() {
        try {
            modelCatalog.resolveRequired(QWEN_PLUS_MODEL_ID);
        } catch (BadRequestException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "未配置千问 API Key（QWEN_API_KEY），无法使用 LangChain for Java 学习功能");
        }
    }

    private void sendEvent(
            SseEmitter emitter,
            AtomicBoolean finished,
            Langchain4jLearningStreamEvent event) {
        if (finished.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(event)));
            if (Langchain4jLearningStreamEvent.STATUS_ERROR.equals(event.getStatus())) {
                finished.set(true);
                emitter.complete();
            }
        } catch (IOException ex) {
            log.debug("SSE 客户端已断开: {}", ex.getMessage());
            finished.set(true);
            emitter.completeWithError(ex);
        }
    }
}
