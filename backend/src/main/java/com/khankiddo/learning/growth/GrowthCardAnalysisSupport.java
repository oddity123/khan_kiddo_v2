package com.khankiddo.learning.growth;

import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.knowledge.HabitCardScorer;
import com.khankiddo.learning.knowledge.HabitScoreInput;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.knowledge.PointScoringSupport;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 成长卡铸卡用的分析装配：从持久化错误行 + 中文表达组装 {@link HabitCardScorer} 输入。
 * <p>
 * 逻辑与 {@code ConversationAnalysisServiceImpl#buildHabitScoreResult} 对齐，
 * 刻意放在 growth 包内以免 Gateway 依赖对话分析 Service。
 */
@Component
@RequiredArgsConstructor
public class GrowthCardAnalysisSupport {

    private final HabitCardScorer habitCardScorer;
    private final PointDictionary pointDictionary;

    public HabitCardScorer.HabitScoreResult score(
            List<ConversationAnalysisItem> rows, List<ChineseExpressionDto> chineseExpressions) {
        if (CollectionUtils.isEmpty(rows)
                || rows.stream().noneMatch(row -> StringUtils.hasText(row.getPointId()))) {
            return new HabitCardScorer.HabitScoreResult(null, List.of(), List.of());
        }

        List<HabitScoreInput.ErrorHit> errorHits = rows.stream()
                .map(row -> new HabitScoreInput.ErrorHit(
                        row.getPointId(),
                        row.getSentenceId() != null ? String.valueOf(row.getSentenceId()) : null,
                        row.getOriginalSentence(),
                        row.getErrorPoint(),
                        row.getSuggestion(),
                        resolveErrorLevel(row.getPointId())))
                .toList();

        return habitCardScorer.score(new HabitScoreInput(errorHits, chineseExpressions));
    }

    private String resolveErrorLevel(String pointId) {
        if (!StringUtils.hasText(pointId)) {
            return null;
        }
        return PointScoringSupport.errorLevel(pointDictionary.resolveOrFallback(pointId));
    }
}
