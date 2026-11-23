package com.khankiddo.learning.memory.mapper;

import com.khankiddo.learning.memory.domain.MemoryEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MemoryEvidenceMapper {

    int insert(MemoryEvidence evidence);

    boolean existsByMemoryIdAndDedupKey(@Param("memoryId") Long memoryId, @Param("dedupKey") String dedupKey);

    List<MemoryEvidence> findByMemoryId(@Param("memoryId") Long memoryId, @Param("limit") Integer limit);

    List<MemoryEvidence> findByMemoryIdAndTypes(@Param("memoryId") Long memoryId, @Param("types") List<String> types);

    Optional<MemoryEvidence> findLatestByMemoryIdAndType(@Param("memoryId") Long memoryId,
                                                         @Param("evidenceType") String evidenceType);

    int countByMemoryIdAndType(@Param("memoryId") Long memoryId, @Param("evidenceType") String evidenceType);

    int countByMemoryIdAndTypeSince(@Param("memoryId") Long memoryId,
                                    @Param("evidenceType") String evidenceType,
                                    @Param("since") LocalDateTime since);

    Optional<LocalDateTime> findLatestCreatedAtByMemoryIdAndType(@Param("memoryId") Long memoryId,
                                                                 @Param("evidenceType") String evidenceType);

    long countByMemoryId(@Param("memoryId") Long memoryId);

    List<Long> findDistinctMemoryIdsByUserIdAndAnalysisId(@Param("userId") Long userId,
                                                          @Param("analysisId") String analysisId);

    int deleteByUserIdAndAnalysisId(@Param("userId") Long userId, @Param("analysisId") String analysisId);

    int deleteByMemoryId(@Param("memoryId") Long memoryId);
}
