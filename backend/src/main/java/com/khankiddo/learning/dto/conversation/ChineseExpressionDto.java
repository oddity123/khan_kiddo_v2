package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 含中文的用户句：表达缺口，不计入语法错误。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChineseExpressionDto {

    /** 在 Stage1 用户句列表中的原始顺序（0-based） */
    private Integer originalIndex;

    /** 用户原句（含中文） */
    private String originalSentence;

    /**
     * 成长卡正面：本句最值得学的中文词/短语线索。
     */
    private String front;

    /**
     * 成长卡背面：对准 {@link #front} 的自然英文对应（词/短短语，或含该短语的口语改写）。
     */
    private String back;

    /** 短中文说明（Phrase Review reason；可空） */
    private String reason;
}
