package com.khankiddo.learning.voice.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.khankiddo.learning.mapper.VoicePracticeEventMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;
import com.khankiddo.learning.voice.VoiceLearningDtoSupport;
import com.khankiddo.learning.voice.VoiceRealtimeFunctionCallSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 给本场追加一条教练备注（可关联本场范围内的卡 / 分析）；不是独立笔记本实体。
 */
@Component
public class AppendPracticeNoteToolHandler extends AbstractVoiceLearningToolHandler {

    public static final String NAME = "append_practice_note";

    private final VoiceLearningToolSupport support;
    private final VoicePracticeEventMapper eventMapper;

    public AppendPracticeNoteToolHandler(VoiceLearningToolSupport support, VoicePracticeEventMapper eventMapper) {
        super(support);
        this.support = support;
        this.eventMapper = eventMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected VoiceRealtimeToolResult doExecute(VoiceRealtimeToolContext ctx, JsonNode args) {
        String text = VoiceLearningToolSupport.text(args, "text");
        if (!StringUtils.hasText(text)) {
            throw new VoiceToolRejectedException("text_required", "text is required");
        }
        String cardId = VoiceLearningToolSupport.text(args, "cardId");
        String analysisId = VoiceLearningToolSupport.text(args, "analysisId");
        GrowthCard card = StringUtils.hasText(cardId) ? support.requireCardInSnapshot(ctx, cardId) : null;
        if (StringUtils.hasText(analysisId)) {
            support.requireAnalysisInSnapshot(ctx, analysisId);
        }

        VoicePracticeEvent row = support.newEvent(ctx, VoicePracticeEvent.TYPE_NOTE);
        row.setNoteText(VoiceLearningToolSupport.clip(text, VoiceLearningToolSupport.MAX_NOTE));
        row.setCardId(card == null ? null : card.getCardId());
        row.setAnalysisId(StringUtils.hasText(analysisId) ? analysisId : null);
        eventMapper.insert(row);

        ObjectNode out = support.mapper().createObjectNode();
        out.put("ok", true);
        out.put("noteId", row.getId() == null ? 0L : row.getId());
        return VoiceRealtimeToolResult.withEvent(
                support.write(out),
                support.panelEvent(
                        VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_NOTE,
                        VoiceLearningDtoSupport.note(row, card)));
    }
}
