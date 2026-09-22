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
    /** 失败时仍可跳转详情页查看原文与错误信息 */
    private String analysisId;
    private MessageStats messageStats;

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
        return error(errorMessage, null);
    }

    public static ConversationAnalysisProgress error(String errorMessage, String analysisId) {
        return ConversationAnalysisProgress.builder()
                .status(STATUS_ERROR)
                .message("分析失败")
                .errorMessage(errorMessage)
                .analysisId(analysisId)
                .build();
    }
}
