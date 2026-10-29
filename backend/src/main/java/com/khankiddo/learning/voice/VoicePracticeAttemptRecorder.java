package com.khankiddo.learning.voice;

import com.khankiddo.learning.growth.GrowthCardScheduler;
import com.khankiddo.learning.growth.GrowthCardStore;
import com.khankiddo.learning.mapper.VoicePracticeEventMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;
import com.khankiddo.learning.voice.tool.VoiceLearningToolSupport;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 回合尝试落库 + 保守晋级：同事务；同场同卡已有一次晋级则不再升。
 */
@Component
@RequiredArgsConstructor
public class VoicePracticeAttemptRecorder {

    private final VoicePracticeEventMapper eventMapper;
    private final GrowthCardStore growthCardStore;
    private final VoiceLearningToolSupport support;

    public record RecordedAttempt(VoicePracticeEvent row, GrowthCard card, String statusAfter) {
    }

    @Transactional
    public RecordedAttempt record(
            VoiceRealtimeToolContext ctx,
            GrowthCard card,
            VoicePracticeAttemptOutcome outcome,
            String userUtterance,
            String reason) {
        String statusBefore = card.getStatus();
        boolean alreadyPromoted = eventMapper.countPromotionsForCard(
                ctx.practiceSessionId(), ctx.userId(), card.getCardId()) > 0;
        Optional<GrowthCardScheduler.ReviewResult> promotion = VoicePracticeProgressPolicy.promotion(
                statusBefore, outcome, LocalDate.now(), alreadyPromoted);
        String statusAfter = promotion.map(GrowthCardScheduler.ReviewResult::status).orElse(null);

        VoicePracticeEvent row = support.newEvent(ctx, VoicePracticeEvent.TYPE_ATTEMPT);
        row.setCardId(card.getCardId());
        row.setOutcome(outcome.wire());
        row.setUserUtterance(VoiceLearningToolSupport.clip(
                userUtterance, VoiceLearningToolSupport.MAX_UTTERANCE));
        row.setReason(VoiceLearningToolSupport.clip(reason, VoiceLearningToolSupport.MAX_REASON));
        row.setStatusBefore(statusBefore);
        row.setStatusAfter(statusAfter);
        eventMapper.insert(row);

        GrowthCard latest = card;
        if (promotion.isPresent() && StringUtils.hasText(statusAfter)) {
            GrowthCardScheduler.ReviewResult next = promotion.get();
            latest = growthCardStore.updateReview(
                    card.getCardId(), ctx.userId(), next.status(), next.nextDueAt());
        }
        return new RecordedAttempt(row, latest, statusAfter);
    }
}
