package com.khankiddo.learning.llm;

import com.khankiddo.learning.dto.conversation.ChineseExpressionDto;
import com.khankiddo.learning.dto.conversation.ExpressionPhraseDto;

import java.util.List;

/**
 * Phrase Review 一次调用的旁路结果：中文 vocab + 英文 expression。
 */
public record PhraseReviewOutcome(
        List<ChineseExpressionDto> chineseExpressions,
        List<ExpressionPhraseDto> expressionPhrases) {

    public static PhraseReviewOutcome empty() {
        return new PhraseReviewOutcome(List.of(), List.of());
    }
}
