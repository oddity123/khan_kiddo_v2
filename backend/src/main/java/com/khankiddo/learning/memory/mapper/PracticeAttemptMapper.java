package com.khankiddo.learning.memory.mapper;

import com.khankiddo.learning.memory.domain.PracticeAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PracticeAttemptMapper {

    int insert(PracticeAttempt attempt);

    List<PracticeAttempt> findBySessionId(@Param("sessionId") Long sessionId);

    List<PracticeAttempt> findByMemoryId(@Param("memoryId") Long memoryId);

    int maxAttemptNo(@Param("sessionId") Long sessionId);

    int deleteBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    int deleteByMemoryId(@Param("memoryId") Long memoryId);
}
