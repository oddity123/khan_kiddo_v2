package com.khankiddo.learning.controller;

import com.khankiddo.learning.config.condition.OnGrammarErrorRagCondition;
import com.khankiddo.learning.dto.grammar.GrammarErrorChatRequest;
import com.khankiddo.learning.rag.grammar.GrammarErrorRagStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversation/grammar-rag")
@Conditional(OnGrammarErrorRagCondition.class)
@RequiredArgsConstructor
public class GrammarErrorRagController {

    private final GrammarErrorRagStreamService streamService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody GrammarErrorChatRequest request) {
        return streamService.chatStream(request.getMessage());
    }
}
