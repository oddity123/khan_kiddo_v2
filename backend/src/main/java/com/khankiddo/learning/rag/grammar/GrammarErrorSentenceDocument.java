package com.khankiddo.learning.rag.grammar;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class GrammarErrorSentenceDocument {

    Long userId;
    String analysisId;
    Long sentenceId;
    List<ConversationAnalysisItem> items;
    LocalDateTime createdAt;

    public static String pointId(Long userId, String analysisId, Long sentenceId) {
        return userId + "_" + analysisId + "_" + sentenceId;
    }
}
