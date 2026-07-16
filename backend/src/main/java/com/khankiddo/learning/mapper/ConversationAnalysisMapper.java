package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.ConversationAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ConversationAnalysisMapper {

    int insert(ConversationAnalysis analysis);

    Optional<ConversationAnalysis> findByAnalysisId(@Param("analysisId") String analysisId);

    Optional<ConversationAnalysis> findByAnalysisIdAndUserId(@Param("analysisId") String analysisId,
                                                             @Param("userId") Long userId);

    List<ConversationAnalysis> findByUserId(@Param("userId") Long userId,
                                            @Param("limit") Integer limit,
                                            @Param("offset") Integer offset);

    long countByUserId(@Param("userId") Long userId);

    /**
     * 统计用户分析次数；{@code days} 为空或 ≤0 表示不限时间。
     */
    long countByUserIdAndStatusAndDays(@Param("userId") Long userId,
                                       @Param("status") String status,
                                       @Param("days") Integer days);

    List<ConversationAnalysis> findByConditions(@Param("userId") Long userId,
                                                @Param("status") String status,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("keyword") String keyword);

    int deleteByAnalysisIdAndUserId(@Param("analysisId") String analysisId, @Param("userId") Long userId);
}
