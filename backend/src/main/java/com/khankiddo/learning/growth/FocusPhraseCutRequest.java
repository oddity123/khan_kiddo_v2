package com.khankiddo.learning.growth;

/**
 * 切短表达输入（原句 + Stage2 point 片段 + suggestion；pointId 供后续灰度）。
 */
public record FocusPhraseCutRequest(
        String originalSentence,
        String point,
        String suggestion,
        String pointId
) {
}
