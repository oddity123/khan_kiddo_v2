package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.model.DailyCount;
import com.khankiddo.learning.model.PointIdCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationAnalysisItemMapper {

    long countDistinctSentencesByUserId(@Param("userId") Long userId);

    long countByUserIdAndPointIds(@Param("userId") Long userId,
                                  @Param("pointIds") List<String> pointIds);

    Map<String, Object> getMostCommonPointIdByUserId(@Param("userId") Long userId);

    long countDistinctSentencesInLast7DaysByUserId(@Param("userId") Long userId);

    long countDistinctSentencesBetweenDaysAgo(@Param("userId") Long userId,
                                              @Param("fromDaysAgo") int fromDaysAgo,
                                              @Param("toDaysAgo") int toDaysAgo);

    List<DailyCount> countDailyDistinctSentencesByUserIdAndDays(@Param("userId") Long userId,
                                                                @Param("days") int days);

    List<DailyCount> countDailyIssuesByUserIdAndDays(@Param("userId") Long userId,
                                                     @Param("days") int days);

    List<ConversationAnalysisItem> findRecentSentencesByUserId(@Param("userId") Long userId,
                                                               @Param("limit") int limit);

    List<ConversationAnalysisItem> findByAnalysisIdAndSentenceId(@Param("analysisId") String analysisId,
                                                                 @Param("sentenceId") Long sentenceId);

    List<ConversationAnalysisItem> findByAnalysisId(@Param("analysisId") String analysisId);

    List<PointIdCount> countPointIdsByUserIdAndDays(@Param("userId") Long userId,
                                                    @Param("days") Integer days);

    List<ConversationAnalysisItem> findErrorExamplesByUserId(@Param("userId") Long userId,
                                                             @Param("pointIds") List<String> pointIds,
                                                             @Param("days") Integer days,
                                                             @Param("limit") int limit);

    long countDistinctErrorSentencesByUserIdAndDays(@Param("userId") Long userId,
                                                    @Param("days") Integer days);

    Map<String, Object> getMostCommonPointIdByUserIdAndDays(@Param("userId") Long userId,
                                                            @Param("days") Integer days);

    int batchInsert(@Param("items") List<ConversationAnalysisItem> items);

    int deleteByAnalysisId(@Param("analysisId") String analysisId);
}
