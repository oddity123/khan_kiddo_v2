package com.khankiddo.learning.service.conversation;

import com.khankiddo.learning.dto.admin.AdminAnalysisListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisProgress;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisRequest;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisResultDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisSaveRequest;

import java.util.function.Consumer;

public interface ConversationAnalysisService {

    ConversationAnalysisResultDto analyze(ConversationAnalysisRequest request,
                                          Consumer<ConversationAnalysisProgress> onProgress);

    /**
     * 仅跑流水线、不落库（游客体验）。
     */
    ConversationAnalysisResultDto analyzeEphemeral(ConversationAnalysisRequest request,
                                                   String analysisId,
                                                   Consumer<ConversationAnalysisProgress> onProgress);

    ConversationAnalysisResultDto analyzeAndPersist(ConversationAnalysisRequest request,
                                                    String analysisId,
                                                    Consumer<ConversationAnalysisProgress> onProgress);

    void saveFailed(String analysisId, String conversationContent, String errorMessage, long processingTimeMs);

    ConversationAnalysisResultDto save(ConversationAnalysisSaveRequest request);

    ConversationAnalysisDetailDto getDetail(String analysisId);

    ConversationAnalysisDetailDto getDetailAsAdmin(String analysisId);

    ConversationAnalysisListResponse list(int page, int size, String keyword);

    ConversationAnalysisListResponse listForUser(Long userId, int page, int size, String keyword);

    AdminAnalysisListResponse listAllAsAdmin(int page, int size, String keyword, String username);

    void delete(String analysisId);
}
