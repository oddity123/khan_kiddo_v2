package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.voice.VoiceLearningPanelResponse;
import com.khankiddo.learning.dto.voice.VoicePracticeBeginRequest;
import com.khankiddo.learning.dto.voice.VoicePracticeBeginResponse;
import com.khankiddo.learning.dto.voice.VoicePracticeSessionResponse;
import com.khankiddo.learning.dto.voice.VoicePracticeTranscriptRequest;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.voice.VoiceLearningPanelService;
import com.khankiddo.learning.voice.VoicePracticeSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录用户的薄练习记录。游客不能开始；额度与字幕都在 voice 包，不进分析服务。
 */
@RestController
@RequestMapping("/api/voice/practice")
@RequiredArgsConstructor
public class VoicePracticeController {

    private final VoicePracticeSessionService sessions;
    private final VoiceLearningPanelService learningPanel;

    @PostMapping("/sessions")
    public VoicePracticeBeginResponse begin(@RequestBody(required = false) VoicePracticeBeginRequest request) {
        return sessions.begin(SecurityUtils.requireUserId(), request);
    }

    @PostMapping("/sessions/{id}/abandon")
    public void abandon(@PathVariable long id) {
        sessions.abandon(SecurityUtils.requireUserId(), id);
    }

    @PutMapping("/sessions/{id}/transcript")
    public VoicePracticeSessionResponse saveTranscript(
            @PathVariable long id,
            @RequestBody VoicePracticeTranscriptRequest request) {
        return sessions.saveTranscript(SecurityUtils.requireUserId(), id, request);
    }

    @GetMapping("/sessions")
    public List<VoicePracticeSessionResponse> recent() {
        return sessions.recent(SecurityUtils.requireUserId());
    }

    @GetMapping("/sessions/{id}")
    public VoicePracticeSessionResponse get(@PathVariable long id) {
        return sessions.get(SecurityUtils.requireUserId(), id);
    }

    @GetMapping("/sessions/{id}/learning")
    public VoiceLearningPanelResponse learning(@PathVariable long id) {
        return learningPanel.load(SecurityUtils.requireUserId(), id);
    }
}
