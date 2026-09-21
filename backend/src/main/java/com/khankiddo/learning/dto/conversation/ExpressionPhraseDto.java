package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stage2 NATURAL 句经 Phrase Review 预计算的短表达对立（挂在 educationalSummary 旁路）。
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

    /** 不自然短片段（成长卡 front） */
    private String focusPhrase;

    /** 地道短说法（成长卡 back） */
    private String suggestion;

    /** 短中文说明 */
    private String reason;
}
