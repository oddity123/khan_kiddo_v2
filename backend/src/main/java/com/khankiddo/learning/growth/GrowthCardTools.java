package com.khankiddo.learning.growth;

import com.khankiddo.learning.model.GrowthCard;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("growthCardTools")
@RequiredArgsConstructor
public class GrowthCardTools {

    private final GrowthCardStore store;

    @Tool(
            name = "persist_growth_card",
            value = "将一张成长卡（练习闪卡）持久化到当前用户卡包。"
                    + "在已根据分析上下文写好 front/back 后调用。"
                    + "同一 sourceAnalysisId+type+sourceRef 重复调用不会重复插入。"
    )
    public String persistGrowthCard(
            @ToolMemoryId Long userId,
            @P(name = "type", description = "habit 或 vocab") String type,
            @P(name = "front", description = "正面：提示/中文/习惯问题") String front,
            @P(name = "back", description = "背面：英文目标说法或要点") String back,
            @P(name = "sourceAnalysisId", description = "来源分析 ID") String sourceAnalysisId,
            @P(name = "sourceRef", description = "幂等键，如 habit:FAM_WORD_FORM 或 vocab:0") String sourceRef) {
        GrowthCard card = store.persistNewOrGet(
                userId, type.trim().toLowerCase(), front, back,
                sourceAnalysisId, sourceRef, null);
        return "ok cardId=" + card.getCardId() + " type=" + card.getType();
    }
}
