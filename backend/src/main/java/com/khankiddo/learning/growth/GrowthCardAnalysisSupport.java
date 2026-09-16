package com.khankiddo.learning.growth;

import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.HabitScoreInput;
import com.khankiddo.learning.knowledge.HabitScoreSupport;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 成长卡铸卡用的分析装配：从持久化错误行调用 {@link HabitCardScorer}。
 * 映射逻辑在 {@link HabitScoreSupport}；本类仅作 growth 包入口，避免 Gateway 依赖对话分析 Service。
 */
@Component
@RequiredArgsConstructor
public class GrowthCardAnalysisSupport {

    private final HabitCardScorer habitCardScorer;
    private final PointDictionary pointDictionary;

    public HabitCardScorer.HabitScoreResult score(List<ConversationAnalysisItem> rows) {
        if (!HabitScoreSupport.hasAnyPointId(rows)) {
            return new HabitCardScorer.HabitScoreResult(null, List.of(), List.of());
        }
        return habitCardScorer.score(new HabitScoreInput(
                HabitScoreSupport.errorHitsFromItems(rows, pointDictionary)));
    }
}
