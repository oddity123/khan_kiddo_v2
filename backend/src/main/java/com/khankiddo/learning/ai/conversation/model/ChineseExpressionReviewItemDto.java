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

    private String suggestion;
}
