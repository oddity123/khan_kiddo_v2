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
     * 成长卡正面（chinese≈中文线索；expression≈中文题干/意图，不地道原文不进 front）。
     */
    private String front;

    /**
     * 成长卡背面（chinese≈英文；expression≈地道表达）。
     */
    private String back;

    /** 短中文说明（为何抽这个 / 学什么 / 为何这样改） */
    private String reason;
}
