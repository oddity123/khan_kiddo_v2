package com.khankiddo.learning.mapper;

import com.khankiddo.learning.model.VoicePracticeEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VoicePracticeEventMapper {

    int insert(VoicePracticeEvent event);

    /** 按发生顺序（id 升序）返回本场全部学习证据。 */
    List<VoicePracticeEvent> findBySession(@Param("practiceSessionId") long practiceSessionId,
                                           @Param("userId") long userId,
                                           @Param("limit") int limit);

    /** 同场同卡已成功晋级的 attempt 数（status_after 非空）。 */
    int countPromotionsForCard(@Param("practiceSessionId") long practiceSessionId,
                               @Param("userId") long userId,
                               @Param("cardId") String cardId);
}
