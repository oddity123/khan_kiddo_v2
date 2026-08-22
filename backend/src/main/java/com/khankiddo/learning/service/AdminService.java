package com.khankiddo.learning.service;

import com.khankiddo.learning.dto.admin.AdminAnalysisListResponse;
import com.khankiddo.learning.dto.admin.AdminUserListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;

public interface AdminService {

    AdminUserListResponse listUsers(int page, int size, String keyword,
                                     Integer minAnalysisCount, Integer maxAnalysisCount);

    ConversationAnalysisListResponse listUserAnalyses(Long userId, int page, int size, String keyword);

    AdminAnalysisListResponse listAnalyses(int page, int size, String keyword, String username);

    ConversationAnalysisDetailDto getAnalysisDetail(String analysisId);
}
