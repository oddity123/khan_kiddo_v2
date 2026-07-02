package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.dto.rag.RagStreamEvent;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.UnauthorizedException;
import com.khankiddo.learning.prompt.PromptLoader;
import com.khankiddo.learning.rag.core.RagChatOrchestrator;
import com.khankiddo.learning.rag.core.SseStreamHelper;
import com.khankiddo.learning.security.SecurityUtils;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@Conditional(OnGrammarErrorRagCondition.class)
public class GrammarErrorRagStreamService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final GrammarErrorRetrievalService retrievalService;
    private final RagChatOrchestrator chatOrchestrator;
    private final PromptLoader promptLoader;
    private final SseStreamHelper sseStreamHelper;

    @Qualifier(GrammarErrorRagConfig.GRAMMAR_RAG_STREAMING_CHAT_MODEL)
    private final StreamingChatModel streamingChatModel;

    public GrammarErrorRagStreamService(
            GrammarErrorRetrievalService retrievalService,
            RagChatOrchestrator chatOrchestrator,
            PromptLoader promptLoader,
            SseStreamHelper sseStreamHelper,
            @Qualifier(GrammarErrorRagConfig.GRAMMAR_RAG_STREAMING_CHAT_MODEL)
            StreamingChatModel streamingChatModel) {
        this.retrievalService = retrievalService;
        this.chatOrchestrator = chatOrchestrator;
        this.promptLoader = promptLoader;
        this.sseStreamHelper = sseStreamHelper;
        this.streamingChatModel = streamingChatModel;
    }

    public SseEmitter chatStream(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BadRequestException("请输入问题");
        }
        Long userId = requireUserId();
        String trimmedMessage = message.trim();
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
                List<EmbeddingMatch<TextSegment>> matches = retrievalService.retrieveForChat(userId, trimmedMessage);
                String systemPrompt = promptLoader.getGrammarRagSystemPrompt();
                chatOrchestrator.streamAnswer(
                        streamingChatModel,
                        systemPrompt,
                        trimmedMessage,
                        matches,
                        new StreamingChatResponseHandler() {
                            @Override
                            public void onPartialResponse(String partialResponse) {
                                sseStreamHelper.sendEvent(
                                        emitter, finished, RagStreamEvent.token(partialResponse));
                            }

                            @Override
                            public void onCompleteResponse(ChatResponse response) {
                                sseStreamHelper.sendEvent(emitter, finished, RagStreamEvent.done());
                                sseStreamHelper.complete(emitter, finished);
                            }

                            @Override
                            public void onError(Throwable error) {
                                log.error("语法错句 RAG 流式回答失败", error);
                                sseStreamHelper.fail(emitter, finished,
                                        StringUtils.hasText(error.getMessage())
                                                ? error.getMessage()
                                                : "流式回答失败");
                            }
                        });
            } catch (Exception ex) {
                log.error("语法错句 RAG 流式任务启动失败", ex);
                sseStreamHelper.fail(emitter, finished,
                        StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "流式回答失败");
            }
        });

        return emitter;
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new UnauthorizedException("未登录");
        }
        return userId;
    }
}
