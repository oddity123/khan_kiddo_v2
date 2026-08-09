package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.GrowthCardEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrowthCardEvidenceMapper {

    int insertIgnore(GrowthCardEvidence evidence);

    int insertIgnoreBatch(@Param("rows") List<GrowthCardEvidence> rows);

    List<GrowthCardEvidence> findByCardId(@Param("cardId") String cardId);

    List<GrowthCardEvidence> findByCardIds(@Param("cardIds") List<String> cardIds);

    /**
     * 按句追踪：某场分析某句关联了哪些成长卡证据行。
     */
    List<GrowthCardEvidence> findByUserAnalysisSentence(@Param("userId") Long userId,
                                                        @Param("sourceAnalysisId") String sourceAnalysisId,
                                                        @Param("sentenceId") String sentenceId);

    int deleteByCardId(@Param("cardId") String cardId);

    long countByCardId(@Param("cardId") String cardId);
}
