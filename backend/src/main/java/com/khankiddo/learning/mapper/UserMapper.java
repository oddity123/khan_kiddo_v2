package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<User> findByUsername(@Param("username") String username);

    Optional<User> findById(@Param("id") Long id);

    int existsByUsername(@Param("username") String username);

    int insert(User user);
}
