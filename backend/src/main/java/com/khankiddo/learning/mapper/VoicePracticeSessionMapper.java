package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.VoicePracticeSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface VoicePracticeSessionMapper {

    int insert(VoicePracticeSession session);

    Optional<VoicePracticeSession> findById(@Param("id") long id);

    int sumUsageSeconds(@Param("userId") Long userId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    int finalizeStaleOpen(@Param("now") LocalDateTime now);

    int updateClose(@Param("id") long id,
                    @Param("endedAt") LocalDateTime endedAt,
                    @Param("wallDurationSeconds") int wallDurationSeconds,
                    @Param("stoppedByCap") boolean stoppedByCap);

    int updateTranscript(@Param("id") long id,
                         @Param("userId") long userId,
                         @Param("transcript") String transcript,
                         @Param("stoppedByCap") boolean stoppedByCap);

    int abandonOpen(@Param("id") long id,
                    @Param("userId") long userId,
                    @Param("endedAt") LocalDateTime endedAt);

    List<VoicePracticeSession> findRecentSaved(@Param("userId") long userId, @Param("limit") int limit);
}
