package com.khankiddo.learning.dto.shadowing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端据此决定是否展示跟读入口；灰度期非管理员两项均为 false。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowingStatusDto {

    /** 识别模型已就绪，可打分 */
    private boolean scoringEnabled;

    /** 可获取原声；为 false 时前端用浏览器朗读 */
    private boolean ttsEnabled;
}
