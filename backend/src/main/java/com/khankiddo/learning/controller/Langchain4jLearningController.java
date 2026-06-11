package com.khankiddo.learning.controller;

import com.khankiddo.learning.config.condition.OnQwenConfiguredCondition;
import com.khankiddo.learning.dto.langchain4j.Langchain4jLearningChatRequest;
import com.khankiddo.learning.service.langchain4j.Langchain4jLearningStreamService;
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
@RequestMapping("/api/langchain4j-learning")
@Conditional(OnQwenConfiguredCondition.class)
@RequiredArgsConstructor
public class Langchain4jLearningController {

    private final Langchain4jLearningStreamService streamService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody Langchain4jLearningChatRequest request) {
        return streamService.chatStream(request.message());
    }
}
