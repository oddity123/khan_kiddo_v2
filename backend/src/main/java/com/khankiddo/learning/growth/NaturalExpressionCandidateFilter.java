package com.khankiddo.learning.growth;

import com.khankiddo.learning.conversation.ErrorPointSpanSupport;
import com.khankiddo.learning.knowledge.PointDictionary;
import com.khankiddo.learning.knowledge.PointScoringSupport;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 短表达积累候选过滤：{@code errorLevel == NATURAL} + 实质 suggestion / 可用 point 片段。
 */
@Component
@RequiredArgsConstructor
public class NaturalExpressionCandidateFilter {

    private final PointDictionary pointDictionary;

    public boolean test(ConversationAnalysisItem item) {
        if (item == null || !StringUtils.hasText(item.getPointId())) {
            return false;
        }
        var definition = pointDictionary.resolveOrFallback(item.getPointId());
        if (!PointScoringSupport.isNatural(definition)) {
            return false;
        }
        return hasUsablePoint(item.getErrorPoint())
                || hasSubstantiveSuggestion(item.getOriginalSentence(), item.getSuggestion());
    }

    private static boolean hasUsablePoint(String errorPoint) {
        ErrorPointSpanSupport.Span span = ErrorPointSpanSupport.parse(errorPoint);
        if (span == null) {
            return false;
        }
        return FocusPhraseTextSupport.hasSubstantiveDiff(span.wrong(), span.correct());
    }

    private static boolean hasSubstantiveSuggestion(String original, String suggestion) {
        if (!StringUtils.hasText(suggestion)) {
            return false;
        }
        return FocusPhraseTextSupport.hasSubstantiveDiff(original, suggestion);
    }
}
