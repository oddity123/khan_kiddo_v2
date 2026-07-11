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

    /** 英文改写或「如何用英文提问」建议 */
    private String suggestion;
}
