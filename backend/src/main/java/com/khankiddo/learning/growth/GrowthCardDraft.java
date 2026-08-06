package com.khankiddo.learning.growth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 生成的知识卡草稿；落库由业务方显式调用 {@link GrowthCardStore}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthCardDraft {

    /** 正面：简短提示或问题 */
    private String front;

    /** 背面：答案 / 目标说法（可附一行必要对照） */
    private String back;
}
