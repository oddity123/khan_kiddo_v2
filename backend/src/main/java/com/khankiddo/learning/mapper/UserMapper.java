package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.User;
import com.khankiddo.learning.model.UserWithAnalysisCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<User> findByUsername(@Param("username") String username);

    Optional<User> findById(@Param("id") Long id);

    int existsByUsername(@Param("username") String username);

    int insert(User user);

    List<User> findAll(@Param("keyword") String keyword,
                       @Param("limit") int limit,
                       @Param("offset") int offset);

    long countAll(@Param("keyword") String keyword);

    List<UserWithAnalysisCount> findAllForAdmin(@Param("keyword") String keyword,
                                                @Param("minAnalysisCount") Integer minAnalysisCount,
                                                @Param("maxAnalysisCount") Integer maxAnalysisCount,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    long countAllForAdmin(@Param("keyword") String keyword,
                          @Param("minAnalysisCount") Integer minAnalysisCount,
                          @Param("maxAnalysisCount") Integer maxAnalysisCount);

    int updateRoleByUsername(@Param("username") String username, @Param("role") String role);
}
