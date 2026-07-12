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
     * 词汇求助时抽出的中文目标词/短语；知识卡片正面优先展示。
     * 内容表达场景可为空，此时正面回退为 {@link #originalSentence}。
     */
    private String focusPhrase;

    /**
     * 词汇求助：目标词的英文对应；内容表达：整句口语英文改写。
     */
    private String suggestion;
}
