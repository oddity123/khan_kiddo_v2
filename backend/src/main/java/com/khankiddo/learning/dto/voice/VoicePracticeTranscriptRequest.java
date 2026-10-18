package com.khankiddo.learning.dto.voice;

import lombok.Data;

import java.util.List;

@Data
public class VoicePracticeTranscriptRequest {

    private List<VoicePracticeCaptionRequest> lines;

    /** 客户端已收到额度事件时为 true；服务端自己停下的标记不会被 false 清掉 */
    private Boolean stoppedByCap;
}
