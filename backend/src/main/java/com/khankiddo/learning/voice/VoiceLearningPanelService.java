package com.khankiddo.learning.voice;

import com.khankiddo.learning.dto.voice.VoiceLearningAttemptDto;
import com.khankiddo.learning.dto.voice.VoiceLearningFocusDto;
import com.khankiddo.learning.dto.voice.VoiceLearningNoteDto;
import com.khankiddo.learning.dto.voice.VoiceLearningPanelResponse;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.mapper.VoicePracticeEventMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取本场学习证据（焦点卡 / 尝试 / 备注），供刷新或回看后恢复右侧面板。
 */
@Service
@RequiredArgsConstructor
public class VoiceLearningPanelService {

    static final int MAX_EVENTS = 500;

    private final VoicePracticeEventMapper eventMapper;
    private final VoicePracticeMaterialAccess materialAccess;
    private final GrowthCardStore growthCardStore;

    public VoiceLearningPanelResponse load(long userId, long practiceSessionId) {
        // requireSnapshot 同时校验场次归属
        VoicePracticeMaterialSnapshot snapshot = materialAccess.requireSnapshot(userId, practiceSessionId);
        List<VoicePracticeEvent> rows = eventMapper.findBySession(practiceSessionId, userId, MAX_EVENTS);
        Map<String, GrowthCard> cards = new HashMap<>();
        for (String cardId : snapshot.cardIds()) {
            growthCardStore.findOwned(cardId, userId).ifPresent(card -> cards.put(cardId, card));
        }
        VoiceLearningFocusDto focus = null;
        List<VoiceLearningAttemptDto> attempts = new ArrayList<>();
        List<VoiceLearningNoteDto> notes = new ArrayList<>();
        if (!CollectionUtils.isEmpty(rows)) {
            for (VoicePracticeEvent row : rows) {
                GrowthCard card = row.getCardId() == null ? null : cards.get(row.getCardId());
                switch (row.getEventType()) {
                    case VoicePracticeEvent.TYPE_FOCUS -> focus = VoiceLearningDtoSupport.focus(row, card);
                    case VoicePracticeEvent.TYPE_ATTEMPT -> attempts.add(VoiceLearningDtoSupport.attempt(row, card));
                    case VoicePracticeEvent.TYPE_NOTE -> notes.add(VoiceLearningDtoSupport.note(row, card));
                    default -> {
                        // 未知类型忽略
                    }
                }
            }
        }
        return new VoiceLearningPanelResponse(focus, attempts, notes);
    }
}
