package com.khankiddo.learning.dto.voice;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class VoicePracticeMaterialSnapshotDto {

    List<String> cardIds;

    List<String> analysisIds;
}
