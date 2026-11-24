package com.khankiddo.learning.memory.mapper;

import com.khankiddo.learning.memory.domain.PracticeSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PracticeSessionMapper {

    int insert(PracticeSession session);

    Optional<PracticeSession> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<PracticeSession> findByUserId(@Param("userId") Long userId,
                                       @Param("memoryId") Long memoryId,
                                       @Param("status") String status);

    List<PracticeSession> findByMemoryId(@Param("memoryId") Long memoryId);

    long countByMemoryId(@Param("memoryId") Long memoryId);

    List<PracticeSession> findInProgressByUserIdAndSourceConversation(
            @Param("userId") Long userId,
            @Param("sourceConversationId") String sourceConversationId);

    int bindSourceConversation(@Param("userId") Long userId,
                               @Param("sessionIds") List<Long> sessionIds,
                               @Param("sourceConversationId") String sourceConversationId);

    int updateCompletion(PracticeSession session);

    /**
     * 仅当会话仍为 IN_PROGRESS 时原子置为终态（用于异步评估幂等防重）。
     */
    int completeIfInProgress(PracticeSession session);

    List<Long> findDistinctMemoryIdsByUserIdAndAnalysisId(@Param("userId") Long userId,
                                                          @Param("analysisId") String analysisId);

    List<Long> findIdsByUserIdAndAnalysisId(@Param("userId") Long userId,
                                            @Param("analysisId") String analysisId);

    int deleteByUserIdAndAnalysisId(@Param("userId") Long userId, @Param("analysisId") String analysisId);

    int deleteByMemoryId(@Param("memoryId") Long memoryId);
}
