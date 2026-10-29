package com.khankiddo.learning.voice;

import java.util.List;

/**
 * 本场练习材料快照：仅 ID 列表，开始时校验归属后落库。
 */
public record VoicePracticeMaterialSnapshot(List<String> cardIds, List<String> analysisIds) {

    public static VoicePracticeMaterialSnapshot empty() {
        return new VoicePracticeMaterialSnapshot(List.of(), List.of());
    }

    public boolean isEmpty() {
        return (cardIds == null || cardIds.isEmpty())
                && (analysisIds == null || analysisIds.isEmpty());
    }
}
