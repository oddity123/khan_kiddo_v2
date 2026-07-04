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

    int batchInsert(@Param("items") List<ConversationAnalysisItem> items);

    int deleteByAnalysisId(@Param("analysisId") String analysisId);
}
