package com.khankiddo.learning.memory.mapper;

import com.khankiddo.learning.memory.domain.WeaknessMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface WeaknessMemoryMapper {

    int insert(WeaknessMemory memory);

    Optional<WeaknessMemory> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Optional<WeaknessMemory> findByUserIdAndPointId(@Param("userId") Long userId, @Param("pointId") String pointId);

    List<WeaknessMemory> findByUserIdAndIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    List<WeaknessMemory> findByUserId(@Param("userId") Long userId, @Param("statuses") List<String> statuses);

    long countAllByUserId(@Param("userId") Long userId);

    int updateMetricsAndStatus(WeaknessMemory memory);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
