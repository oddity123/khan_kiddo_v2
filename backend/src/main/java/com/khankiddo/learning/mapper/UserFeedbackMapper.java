package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.UserFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFeedbackMapper {

    int insert(UserFeedback feedback);
}
