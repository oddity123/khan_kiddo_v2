package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stage2 NATURAL 句经 Phrase Review 预计算的短表达（挂在 educationalSummary 旁路）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionPhraseDto {

    /** 与 {@code ConversationAnalysisItem.sentenceId} / sourceRef {@code expr:{id}} 对齐 */
    private Long sentenceId;

    private String pointId;

    private String originalSentence;

    /** 成长卡正面（中文题干/意图提示；不地道原文不进 front） */
    private String front;

    /** 成长卡背面（地道表达） */
    private String back;

    /** 短中文说明 */
    private String reason;
}
