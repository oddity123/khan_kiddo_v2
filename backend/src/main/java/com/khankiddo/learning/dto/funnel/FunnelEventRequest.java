package com.khankiddo.learning.dto.funnel;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FunnelEventRequest {

    @NotBlank(message = "事件名不能为空")
    private String name;

    /** 仅 {@code voice_end} 使用，墙钟秒 */
    private Integer durationSeconds;

    /** {@code voice_saved} / {@code voice_to_analyze} 带上已保存的练习记录 id */
    private Long voiceSessionId;
}
