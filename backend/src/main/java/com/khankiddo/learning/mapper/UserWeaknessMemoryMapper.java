package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.UserWeaknessMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserWeaknessMemoryMapper {

    int insert(UserWeaknessMemory memory);

    int updateMerged(UserWeaknessMemory memory);

    int archiveById(@Param("id") Long id, @Param("userId") Long userId);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int deleteByUserIdAndSourceAnalysisId(@Param("userId") Long userId,
                                          @Param("sourceAnalysisId") String sourceAnalysisId);

    Optional<UserWeaknessMemory> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<UserWeaknessMemory> findActiveByUserId(@Param("userId") Long userId);

    long countActiveByUserId(@Param("userId") Long userId);

    /**
     * 活跃池淘汰候选：hit_count 升序，同次数再按 last_seen_at 升序。
     */
    List<UserWeaknessMemory> findActiveForEviction(@Param("userId") Long userId,
                                                   @Param("limit") int limit);

    boolean existsOriginalSentenceByUserId(@Param("userId") Long userId,
                                           @Param("originalSentence") String originalSentence);
}
