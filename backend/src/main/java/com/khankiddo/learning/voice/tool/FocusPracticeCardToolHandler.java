package com.khankiddo.learning.voice.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.khankiddo.learning.mapper.VoicePracticeEventMapper;
import com.khankiddo.learning.model.GrowthCard;
import com.khankiddo.learning.model.VoicePracticeEvent;
import com.khankiddo.learning.voice.VoiceLearningDtoSupport;
import com.khankiddo.learning.voice.VoiceRealtimeFunctionCallSupport;
import org.springframework.stereotype.Component;

/**
 * 把本场某张卡设为当前焦点；落库后右侧面板展示该卡。
 */
@Component
public class FocusPracticeCardToolHandler extends AbstractVoiceLearningToolHandler {

    public static final String NAME = "focus_practice_card";

    private final VoiceLearningToolSupport support;
    private final VoicePracticeEventMapper eventMapper;

    public FocusPracticeCardToolHandler(VoiceLearningToolSupport support, VoicePracticeEventMapper eventMapper) {
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
        GrowthCard card = support.requireCardInSnapshot(ctx, VoiceLearningToolSupport.text(args, "cardId"));
        VoicePracticeEvent row = support.newEvent(ctx, VoicePracticeEvent.TYPE_FOCUS);
        row.setCardId(card.getCardId());
        row.setNoteText(VoiceLearningToolSupport.clip(
                VoiceLearningToolSupport.text(args, "promptHint"), VoiceLearningToolSupport.MAX_NOTE));
        eventMapper.insert(row);

        ObjectNode out = support.mapper().createObjectNode();
        out.put("ok", true);
        out.put("cardId", card.getCardId());
        out.put("front", card.getFront());
        out.put("back", card.getBack());
        out.put("status", card.getStatus());
        return VoiceRealtimeToolResult.withEvent(
                support.write(out),
                support.panelEvent(
                        VoiceRealtimeFunctionCallSupport.EVENT_LEARNING_FOCUS,
                        VoiceLearningDtoSupport.focus(row, card)));
    }
}
