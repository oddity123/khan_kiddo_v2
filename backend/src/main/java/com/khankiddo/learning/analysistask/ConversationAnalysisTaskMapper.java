package com.khankiddo.learning.analysistask;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface ConversationAnalysisTaskMapper {

    int insert(ConversationAnalysisTask task);

    Optional<ConversationAnalysisTask> findByTaskId(@Param("taskId") String taskId);

    /**
     * 同用户 + 同来源对话 + 同内容指纹，且未失败 → 可复用。
     */
    Optional<ConversationAnalysisTask> findReusableBySourceAndFingerprint(
            @Param("userId") long userId,
            @Param("sourceType") String sourceType,
            @Param("sourceConversationId") String sourceConversationId,
            @Param("contentFingerprint") String contentFingerprint);

    /** 无来源对话 id 时（如纯内容提交）按指纹复用。 */
    Optional<ConversationAnalysisTask> findReusableByFingerprint(
            @Param("userId") long userId,
            @Param("contentFingerprint") String contentFingerprint);

    /** 同用户 + 来源对话的最新一条（含 FAILED），用于状态确认。 */
    Optional<ConversationAnalysisTask> findLatestBySource(
            @Param("userId") long userId,
            @Param("sourceType") String sourceType,
            @Param("sourceConversationId") String sourceConversationId);

    int markRunning(@Param("taskId") String taskId,
                    @Param("startedAt") LocalDateTime startedAt,
                    @Param("expectedStatus") String expectedStatus);

    int markSucceeded(@Param("taskId") String taskId,
                      @Param("analysisId") String analysisId,
                      @Param("finishedAt") LocalDateTime finishedAt,
                      @Param("expectedStatus") String expectedStatus);

    int markFailed(@Param("taskId") String taskId,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt,
                   @Param("expectedStatus") String expectedStatus);

    int deleteByAnalysisIdAndUserId(@Param("analysisId") String analysisId,
                                    @Param("userId") long userId);
}
