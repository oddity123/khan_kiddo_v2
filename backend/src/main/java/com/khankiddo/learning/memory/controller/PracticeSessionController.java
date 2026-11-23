package com.khankiddo.learning.memory.controller;

import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.dto.BindPracticeConversationRequest;
import com.khankiddo.learning.memory.dto.PracticeSessionResponse;
import com.khankiddo.learning.memory.dto.RecordPracticeAttemptRequest;
import com.khankiddo.learning.memory.dto.StartPracticeSessionRequest;
import com.khankiddo.learning.memory.enums.PracticeType;
import com.khankiddo.learning.memory.service.MemoryPracticeService;
import com.khankiddo.learning.memory.service.MemoryService;
import com.khankiddo.learning.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/practice/sessions")
@RequiredArgsConstructor
public class PracticeSessionController {

    private final MemoryPracticeService practiceService;

    @PostMapping
    public List<PracticeSessionResponse> start(@Valid @RequestBody StartPracticeSessionRequest request) {
        PracticeType practiceType;
        try {
            practiceType = PracticeType.fromStringOrDefault(request.getPracticeType(), PracticeType.SCENARIO);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("不支持的练习类型: " + request.getPracticeType());
        }
        List<PracticeSession> sessions = practiceService.startSessions(
                SecurityUtils.requireUserId(),
                request.getMemoryIds(),
                practiceType,
                request.getSourceConversationId());
        return sessions.stream()
                .map(session -> MemoryService.toSessionResponse(session, List.of()))
                .toList();
    }

    @PostMapping("/bind-conversation")
    public Map<String, Integer> bindConversation(@Valid @RequestBody BindPracticeConversationRequest request) {
        int updated = practiceService.bindConversation(
                SecurityUtils.requireUserId(),
                request.getSessionIds(),
                request.getSourceConversationId());
        return Map.of("boundCount", updated);
    }

    @GetMapping
    public List<PracticeSessionResponse> list(
            @RequestParam(required = false) Long memoryId,
            @RequestParam(required = false) String status) {
        return practiceService.listSessions(SecurityUtils.requireUserId(), memoryId, status);
    }

    @PostMapping("/{sessionId}/attempts")
    public PracticeSessionResponse recordAttempt(
            @PathVariable Long sessionId,
            @Valid @RequestBody RecordPracticeAttemptRequest request) {
        return practiceService.recordAttempt(SecurityUtils.requireUserId(), sessionId, request);
    }
}
