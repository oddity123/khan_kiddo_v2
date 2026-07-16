package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.ProblemTypeCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationAnalysisItemMapper {

    long countDistinctSentencesByUserId(@Param("userId") Long userId);

    long countByUserIdAndProblemTypes(@Param("userId") Long userId,
                                      @Param("problemTypes") List<String> problemTypes);

    Map<String, Object> getMostCommonProblemTypeByUserId(@Param("userId") Long userId);

    long countDistinctSentencesInLast7DaysByUserId(@Param("userId") Long userId);

    List<ConversationAnalysisItem> findRecentSentencesByUserId(@Param("userId") Long userId,
                                                               @Param("limit") int limit);

    List<ConversationAnalysisItem> findByAnalysisIdAndSentenceId(@Param("analysisId") String analysisId,
                                                                 @Param("sentenceId") Long sentenceId);

    List<ConversationAnalysisItem> findByAnalysisId(@Param("analysisId") String analysisId);

    List<ProblemTypeCount> countProblemTypesByUserId(@Param("userId") Long userId);

    /**
     * 按用户统计错误类型；{@code days} 为空或 ≤0 表示不限时间。
     */
    List<ProblemTypeCount> countProblemTypesByUserIdAndDays(@Param("userId") Long userId,
                                                            @Param("days") Integer days);

    /**
     * 最近错句样例（按错误行）；可按类型与近 N 天过滤。
     */
    List<ConversationAnalysisItem> findErrorExamplesByUserId(@Param("userId") Long userId,
                                                             @Param("problemTypes") List<String> problemTypes,
                                                             @Param("days") Integer days,
                                                             @Param("limit") int limit);

    long countDistinctErrorSentencesByUserIdAndDays(@Param("userId") Long userId,
                                                    @Param("days") Integer days);

    Map<String, Object> getMostCommonProblemTypeByUserIdAndDays(@Param("userId") Long userId,
                                                                @Param("days") Integer days);

    int batchInsert(@Param("items") List<ConversationAnalysisItem> items);

    int deleteByAnalysisId(@Param("analysisId") String analysisId);
}
