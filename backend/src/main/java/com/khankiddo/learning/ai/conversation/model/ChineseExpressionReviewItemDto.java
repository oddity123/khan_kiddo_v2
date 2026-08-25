package com.khankiddo.learning.ai.conversation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChineseExpressionReviewItemDto {

    /** 与 prompt 中编号一致（1-based） */
    private int index;

    /**
     * 本句最值得学的中文词/短语（应非空）。
     */
    private String focusPhrase;

    private String suggestion;
}
