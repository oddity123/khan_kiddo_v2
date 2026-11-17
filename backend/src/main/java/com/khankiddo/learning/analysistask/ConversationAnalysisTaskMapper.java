package com.khankiddo.learning.analysistask;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface ConversationAnalysisTaskMapper {

    int insert(ConversationAnalysisTask task);

    Optional<ConversationAnalysisTask> findByTaskId(@Param("taskId") String taskId);

    Optional<ConversationAnalysisTask> findReusable(
            @Param("userId") long userId,
            @Param("contentFingerprint") String contentFingerprint);

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
}
