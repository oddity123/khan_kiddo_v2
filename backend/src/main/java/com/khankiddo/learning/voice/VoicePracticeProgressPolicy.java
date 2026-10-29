package com.khankiddo.learning.voice;

import com.khankiddo.learning.growth.GrowthCardScheduler;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 语音练习的保守进度：仅「独立成功」且最多上调一级（unfamiliar→fuzzy→mastered）；
 * 从不降级，prompted / uncertain / miss 一律不动。复用 {@link GrowthCardScheduler} 的间隔，不另造时间表。
 */
public final class VoicePracticeProgressPolicy {

    private static final GrowthCardScheduler SCHEDULER = new GrowthCardScheduler();

    private VoicePracticeProgressPolicy() {
    }

    /**
     * @param alreadyPromotedInSession 同场同卡是否已有一次晋级证据；为 true 时不再升
     * @return 需要写入的新状态与下次到期日；无需变更时为空
     */
    public static Optional<GrowthCardScheduler.ReviewResult> promotion(
            String currentStatus,
            VoicePracticeAttemptOutcome outcome,
            LocalDate today,
            boolean alreadyPromotedInSession) {
        if (alreadyPromotedInSession
                || outcome != VoicePracticeAttemptOutcome.INDEPENDENT_SUCCESS
                || currentStatus == null) {
            return Optional.empty();
        }
        return switch (currentStatus) {
            case "unfamiliar" -> Optional.of(SCHEDULER.apply("good", today));
            case "fuzzy" -> Optional.of(SCHEDULER.apply("easy", today));
            default -> Optional.empty();
        };
    }

    /** 默认本场尚未晋级过该卡。 */
    public static Optional<GrowthCardScheduler.ReviewResult> promotion(
            String currentStatus, VoicePracticeAttemptOutcome outcome, LocalDate today) {
        return promotion(currentStatus, outcome, today, false);
    }
}
