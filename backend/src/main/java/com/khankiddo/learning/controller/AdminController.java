package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.admin.AdminAnalysisListResponse;
import com.khankiddo.learning.dto.admin.AdminPointDictionaryResponse;
import com.khankiddo.learning.dto.admin.AdminUserListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;
import com.khankiddo.learning.service.AdminKnowledgeService;
import com.khankiddo.learning.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminKnowledgeService adminKnowledgeService;

    @GetMapping("/users")
    public AdminUserListResponse listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minAnalysisCount,
            @RequestParam(required = false) Integer maxAnalysisCount) {
        return adminService.listUsers(page, size, keyword, minAnalysisCount, maxAnalysisCount);
    }

    @GetMapping("/users/{userId}/analyses")
    public ConversationAnalysisListResponse listUserAnalyses(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return adminService.listUserAnalyses(userId, page, size, keyword);
    }

    @GetMapping("/analyses")
    public AdminAnalysisListResponse listAnalyses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username) {
        return adminService.listAnalyses(page, size, keyword, username);
    }

    @GetMapping("/analyses/{analysisId}")
    public ConversationAnalysisDetailDto getAnalysisDetail(@PathVariable String analysisId) {
        return adminService.getAnalysisDetail(analysisId);
    }

    @GetMapping("/knowledge/point-dictionary")
    public AdminPointDictionaryResponse getPointDictionary() {
        return adminKnowledgeService.getPointDictionary();
    }
}
