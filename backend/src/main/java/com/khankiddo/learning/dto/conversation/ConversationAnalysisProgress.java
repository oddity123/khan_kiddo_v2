package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationAnalysisProgress {

    public static final String STATUS_START = "START";
    public static final String STATUS_VALIDATING = "VALIDATING";
    public static final String STATUS_SEPARATING = "SEPARATING";
    public static final String STATUS_ANALYZING = "ANALYZING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_SUMMARIZING = "SUMMARIZING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ERROR = "ERROR";

    private String status;
    private String message;
    private ConversationAnalysisResultDto result;
    private String errorMessage;
    private MessageStats messageStats;

    /** Stage 2 流式预览：原句（可不完整，以 "..." 结尾） */
    private String streamingOriginal;
    /** Stage 2 流式预览：建议 */
    private String streamingSuggestion;
    /** Stage 2 流式预览：错误 hint，如 "2 个错误" */
    private String streamingErrorsHint;
    /** 上一句提交时的原句（用于前端追加卡片） */
    private String streamingCommitOriginal;
    private String streamingCommitSuggestion;
    private String streamingCommitErrorsHint;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageStats {
        private Integer totalMessages;
        private Integer userMessages;
        private Integer aiMessages;
    }

    public static ConversationAnalysisProgress of(String status, String message) {
        return ConversationAnalysisProgress.builder().status(status).message(message).build();
    }

    public static ConversationAnalysisProgress complete(ConversationAnalysisResultDto result) {
        return ConversationAnalysisProgress.builder()
                .status(STATUS_COMPLETED)
                .message("分析完成")
                .result(result)
                .build();
    }

    public static ConversationAnalysisProgress error(String errorMessage) {
        return ConversationAnalysisProgress.builder()
                .status(STATUS_ERROR)
                .message("分析失败")
                .errorMessage(errorMessage)
                .build();
    }
}
