package com.khankiddo.learning.service.conversation;

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

    ConversationAnalysisResultDto analyzeAndPersist(ConversationAnalysisRequest request,
                                                    String analysisId,
                                                    Consumer<ConversationAnalysisProgress> onProgress);

    void saveFailed(String analysisId, String conversationContent, String errorMessage, long processingTimeMs);

    ConversationAnalysisResultDto save(ConversationAnalysisSaveRequest request);

    ConversationAnalysisDetailDto getDetail(String analysisId);

    ConversationAnalysisListResponse list(int page, int size, String keyword);

    void delete(String analysisId);
}
