package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.GrowthCardStatusCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface GrowthCardMapper {

    int insert(GrowthCard card);

    Optional<GrowthCard> findByCardIdAndUserId(@Param("cardId") String cardId,
                                               @Param("userId") Long userId);

    Optional<GrowthCard> findByUserSource(@Param("userId") Long userId,
                                          @Param("sourceAnalysisId") String sourceAnalysisId,
                                          @Param("type") String type,
                                          @Param("sourceRef") String sourceRef);

    List<GrowthCard> findDueByUserId(@Param("userId") Long userId,
                                     @Param("today") LocalDate today);

    List<GrowthCard> findRandomByUserId(@Param("userId") Long userId,
                                        @Param("limit") int limit);

    long countDueByUserId(@Param("userId") Long userId,
                          @Param("today") LocalDate today);

    List<GrowthCardStatusCount> countStatusByUserId(@Param("userId") Long userId);

    int updateReview(@Param("cardId") String cardId,
                     @Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("nextDueAt") LocalDate nextDueAt);

    int deleteByCardIdAndUserId(@Param("cardId") String cardId,
                                @Param("userId") Long userId);

    Optional<GrowthCard> findHabitByAnalysis(@Param("userId") Long userId,
                                               @Param("analysisId") String analysisId);

    List<GrowthCard> findByUserAndAnalysis(@Param("userId") Long userId,
                                           @Param("analysisId") String analysisId);
}
