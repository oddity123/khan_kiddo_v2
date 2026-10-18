package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.FunnelEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FunnelEventMapper {

    int insert(FunnelEvent event);
}
