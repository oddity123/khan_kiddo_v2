package com.khankiddo.learning.dto.voice;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoicePracticeBeginResponse {

    long id;

    /** 这场最多还能说的墙钟秒，来自 Properties 里的帽，不是页面常量 */
    int grantedSeconds;

    VoicePracticeMaterialSnapshotDto materials;
}
