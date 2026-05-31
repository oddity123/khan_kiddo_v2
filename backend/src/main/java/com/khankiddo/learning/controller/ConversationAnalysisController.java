package com.khankiddo.learning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisSaveRequest;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationAnalysisController {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ConversationAnalysisService conversationAnalysisService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@Valid @RequestBody ConversationAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);
        SecurityContext securityContext = SecurityContextHolder.getContext();

        emitter.onCompletion(() -> finished.set(true));
        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> finished.set(true));

        Thread.startVirtualThread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ConversationAnalysisResultDto result = conversationAnalysisService.analyze(
                        request, progress -> sendProgress(emitter, finished, progress));
                sendProgress(emitter, finished, ConversationAnalysisProgress.complete(result));
            } catch (Exception ex) {
                log.error("对话分析流式任务失败", ex);
                sendProgress(emitter, finished, ConversationAnalysisProgress.error(ex.getMessage()));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
    }

    @PostMapping("/analyses")
    public ConversationAnalysisResultDto save(@Valid @RequestBody ConversationAnalysisSaveRequest request) {
        return conversationAnalysisService.save(request);
    }

    @GetMapping("/analyses")
    public ConversationAnalysisListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return conversationAnalysisService.list(page, size, keyword);
    }

    @GetMapping("/analyses/{analysisId}")
    public ConversationAnalysisDetailDto detail(@PathVariable String analysisId) {
        return conversationAnalysisService.getDetail(analysisId);
    }

    @DeleteMapping("/analyses/{analysisId}")
    public void delete(@PathVariable String analysisId) {
        conversationAnalysisService.delete(analysisId);
    }

    private void sendProgress(
            SseEmitter emitter,
            AtomicBoolean finished,
            ConversationAnalysisProgress progress) {
        if (finished.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("progress").data(objectMapper.writeValueAsString(progress)));
            if (ConversationAnalysisProgress.STATUS_COMPLETED.equals(progress.getStatus())
                    || ConversationAnalysisProgress.STATUS_ERROR.equals(progress.getStatus())) {
                finished.set(true);
                emitter.complete();
            }
        } catch (IOException ex) {
            log.debug("SSE 客户端已断开: {}", ex.getMessage());
            finished.set(true);
            emitter.completeWithError(ex);
        } catch (Exception ex) {
            log.error("SSE 推送失败", ex);
            finished.set(true);
            emitter.completeWithError(ex);
        }
    }
}
