package com.khankiddo.learning.funnel;

import com.khankiddo.learning.dto.funnel.FunnelEventRequest;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.mapper.FunnelEventMapper;
import com.khankiddo.learning.model.FunnelEvent;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

/**
 * 只落白名单事件。{@code analyze_start} 的游客标记由登录态决定，不信客户端自报。
 */
@Service
@RequiredArgsConstructor
public class FunnelEventService {

    private final FunnelEventMapper mapper;

    public void record(Long userId, FunnelEventRequest request) {
        FunnelEventName name = FunnelEventName.parse(request == null ? null : request.getName());
        if (name == null) {
            throw new BadRequestException("不记录该事件");
        }
        Integer durationSeconds = null;
        if (name == FunnelEventName.voice_end) {
            if (request.getDurationSeconds() == null || request.getDurationSeconds() < 0) {
                throw new BadRequestException("voice_end 需要非负时长");
            }
            durationSeconds = request.getDurationSeconds();
        }
        Long voiceSessionId = null;
        if (request.getVoiceSessionId() != null && request.getVoiceSessionId() > 0) {
            voiceSessionId = request.getVoiceSessionId();
        }
        Boolean guest = null;
        if (name == FunnelEventName.analyze_start) {
            guest = ObjectUtils.isEmpty(userId);
        }
        mapper.insert(FunnelEvent.builder()
                .userId(userId)
                .eventName(name.name())
                .guest(guest)
                .durationSeconds(durationSeconds)
                .voiceSessionId(voiceSessionId)
                .build());
    }
}
