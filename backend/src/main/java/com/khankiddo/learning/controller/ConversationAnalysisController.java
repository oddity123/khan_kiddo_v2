package com.khankiddo.learning.controller;

import com.khankiddo.learning.conversation.GuestAnalysisQuotaService;
import com.khankiddo.learning.dto.conversation.*;
import com.khankiddo.learning.llm.LlmModelCatalog;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import com.khankiddo.learning.service.conversation.ConversationAnalysisStreamService;
import com.khankiddo.learning.service.conversation.PracticePromptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final GuestAnalysisQuotaService guestAnalysisQuotaService;
    private final PracticePromptService practicePromptService;

    @GetMapping("/llm-models")
    public List<LlmModelOptionDto> listLlmModels() {
        return llmModelCatalog.listEnabled();
    }

    @GetMapping("/guest-quota")
    public GuestQuotaDto guestQuota(HttpServletRequest request) {
        GuestAnalysisQuotaService.GuestQuotaSnapshot snapshot = guestAnalysisQuotaService.snapshot(request);
        return GuestQuotaDto.builder()
                .limit(snapshot.limit())
                .used(snapshot.used())
                .remaining(snapshot.remaining())
                .build();
    }

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(
            @Valid @RequestBody ConversationAnalysisRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return conversationAnalysisStreamService.analyzeStream(request, httpRequest, httpResponse);
    }

    @PostMapping("/practice-prompt")
    public PracticePromptResponse practicePrompt(@Valid @RequestBody PracticePromptRequest request) {
        return practicePromptService.assemble(request);
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
