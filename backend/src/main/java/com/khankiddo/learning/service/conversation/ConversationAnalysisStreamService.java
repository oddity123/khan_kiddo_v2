package com.khankiddo.learning.service.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationAnalysisStreamService {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ConversationAnalysisService conversationAnalysisService;
    private final ObjectMapper objectMapper;

    public SseEmitter analyzeStream(ConversationAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);
        String analysisId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();

        emitter.onCompletion(() -> finished.set(true));
        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> finished.set(true));

        SecurityContext securityContext = SecurityContextHolder.getContext();
        Thread.startVirtualThread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ConversationAnalysisResultDto result = conversationAnalysisService.analyzeAndPersist(
                        request, analysisId, progress -> sendProgress(emitter, finished, progress));
                sendProgress(emitter, finished, ConversationAnalysisProgress.complete(result));
            } catch (Exception ex) {
                log.error("对话分析流式任务失败, analysisId={}", analysisId, ex);
                long elapsed = System.currentTimeMillis() - startedAt;
                conversationAnalysisService.saveFailed(
                        analysisId,
                        request.getConversationContent(),
                        ex.getMessage(),
                        elapsed);
                sendProgress(emitter, finished,
                        ConversationAnalysisProgress.error(ex.getMessage(), analysisId));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
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
