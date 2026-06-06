package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.conversation.*;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import com.khankiddo.learning.service.conversation.ConversationAnalysisStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationAnalysisController {

    private final ConversationAnalysisService conversationAnalysisService;
    private final ConversationAnalysisStreamService conversationAnalysisStreamService;
    private final LlmModelCatalog llmModelCatalog;

    @GetMapping("/llm-models")
    public List<LlmModelOptionDto> listLlmModels() {
        return llmModelCatalog.listEnabled();
    }

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
