package com.khankiddo.learning.ai.conversation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhraseCardReviewItemDto {

    /** chinese | expression */
    private String kind;

    /** 与 prompt 中编号一致（1-based） */
    private int index;

    /**
     * 闪卡正面短焦点（chinese=中文目标；expression=不自然短片段）。
     */
    private String focusPhrase;

    /**
     * 闪卡背面短对应（chinese=英文；expression=地道短说法）。
     */
    private String suggestion;

    /** 短中文说明（为何抽这个 / 学什么 / 为何这样改） */
    private String reason;
}
