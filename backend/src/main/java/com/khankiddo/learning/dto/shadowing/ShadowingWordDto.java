package com.khankiddo.learning.dto.shadowing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目标句中的一个展示词（按空白切分，保留原始大小写与标点）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowingWordDto {

    private String text;

    /** 是否在识别结果中按顺序命中；不计分的词恒为 false */
    private boolean hit;

    /** 是否参与计分（含数字或仅标点的词不计分） */
    private boolean scored;
}
