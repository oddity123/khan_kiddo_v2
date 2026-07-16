package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.grammar.GrammarErrorChatRequest;
import com.khankiddo.learning.rag.grammar.GrammarErrorRagStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 语法复盘（SSE）：入口不再依赖 Qdrant 条件——无向量库时语义检索透传，DB Tools 仍可用。
 */
@RestController
@RequestMapping("/api/conversation/grammar-rag")
@RequiredArgsConstructor
public class GrammarErrorRagController {

    private final GrammarErrorRagStreamService streamService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody GrammarErrorChatRequest request) {
        return streamService.chatStream(request.getMessage());
    }
}
