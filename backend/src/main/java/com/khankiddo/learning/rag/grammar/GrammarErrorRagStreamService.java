package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.ai.grammar.GrammarStatsAssistant;
import com.khankiddo.learning.dto.rag.RagStreamEvent;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.rag.core.SseStreamHelper;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 语法复盘流式问答：SSE 管道 + {@link GrammarStatsAssistant#chatStream(Long, String)}。
 * <p>
 * userId 在请求线程上取自 SecurityContext，随 {@code @MemoryId} 进入 AiService；
 * 之后的检索过滤与 Tool 执行均从 memoryId 取值，不再依赖 ThreadLocal。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrammarErrorRagStreamService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final GrammarStatsAssistant grammarStatsAssistant;
    private final SseStreamHelper sseStreamHelper;

    public SseEmitter chatStream(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BadRequestException("请输入问题");
        }
        Long userId = SecurityUtils.requireUserId();
        String trimmedMessage = message.trim();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);

        emitter.onCompletion(() -> finished.set(true));
        emitter.onTimeout(() -> {
            finished.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> finished.set(true));

        // 工具循环（DB 查询、语义检索）耗时较长，放到虚拟线程避免阻塞请求线程
        Thread.startVirtualThread(() -> {
            try {
                grammarStatsAssistant.chatStream(userId, trimmedMessage)
                        .onPartialResponse(token ->
                                sseStreamHelper.sendEvent(emitter, finished, RagStreamEvent.token(token)))
                        .onToolExecuted(toolExecution ->
                                log.debug("语法复盘工具调用: {}", toolExecution.request().name()))
                        .onCompleteResponse(response -> {
                            sseStreamHelper.sendEvent(emitter, finished, RagStreamEvent.done());
                            sseStreamHelper.complete(emitter, finished);
                        })
                        .onError(error -> {
                            log.error("语法复盘流式回答失败", error);
                            sseStreamHelper.fail(emitter, finished, error.getMessage());
                        })
                        .start();
            } catch (Exception ex) {
                log.error("语法复盘流式任务启动失败", ex);
                sseStreamHelper.fail(emitter, finished, ex.getMessage());
            }
        });

        return emitter;
    }
}
