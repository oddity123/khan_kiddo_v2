package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisSaveRequest;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import com.khankiddo.learning.service.conversation.ConversationAnalysisStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationAnalysisController {

    private final ConversationAnalysisService conversationAnalysisService;
    private final ConversationAnalysisStreamService conversationAnalysisStreamService;

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@Valid @RequestBody ConversationAnalysisRequest request) {
        return conversationAnalysisStreamService.analyzeStream(request);
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
}
