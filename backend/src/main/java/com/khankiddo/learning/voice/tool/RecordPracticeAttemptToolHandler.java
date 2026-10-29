package com.khankiddo.learning.voice.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.voice.VoiceLearningDtoSupport;
import com.khankiddo.learning.voice.VoicePracticeAttemptOutcome;
import com.khankiddo.learning.voice.VoicePracticeAttemptRecorder;
import com.khankiddo.learning.voice.VoiceRealtimeFunctionCallSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 记录一次回合尝试证据；仅「独立成功」且同场同卡尚未晋级时，按 Policy 保守上调一级。
 */
@Component
public class RecordPracticeAttemptToolHandler extends AbstractVoiceLearningToolHandler {

    public static final String NAME = "record_practice_attempt";

    private final VoiceLearningToolSupport support;
    private final VoicePracticeAttemptRecorder recorder;

    public RecordPracticeAttemptToolHandler(
            VoiceLearningToolSupport support,
            VoicePracticeAttemptRecorder recorder) {
        super(support);
        this.support = support;
        this.recorder = recorder;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected VoiceRealtimeToolResult doExecute(VoiceRealtimeToolContext ctx, JsonNode args) {
        VoicePracticeAttemptOutcome outcome = VoicePracticeAttemptOutcome
                .fromWire(VoiceLearningToolSupport.text(args, "outcome"))
                .orElseThrow(() -> new VoiceToolRejectedException(
                        "invalid_outcome",
                        "outcome must be independent_success | prompted | uncertain | miss"));
        GrowthCard card = support.requireCardInSnapshot(ctx, VoiceLearningToolSupport.text(args, "cardId"));

        VoicePracticeAttemptRecorder.RecordedAttempt recorded = recorder.record(
                ctx,
                card,
                outcome,
                VoiceLearningToolSupport.text(args, "userUtterance"),
                VoiceLearningToolSupport.text(args, "reason"));

        ObjectNode out = support.mapper().createObjectNode();
        out.put("ok", true);
        out.put("attemptId", recorded.row().getId() == null ? 0L : recorded.row().getId());
        out.put("cardId", recorded.card().getCardId());
        out.put("outcome", outcome.wire());
        out.put("status", recorded.card().getStatus());
        if (StringUtils.hasText(recorded.statusAfter())) {
            out.put("newStatus", recorded.statusAfter());
        }
        return VoiceRealtimeToolResult.withEvent(
                support.write(out),
                support.panelEvent(
                        VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_ATTEMPT,
                        VoiceLearningDtoSupport.attempt(recorded.row(), recorded.card())));
    }
}
