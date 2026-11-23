package com.khankiddo.learning.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryDetailResponse {

    private MemoryItemResponse memory;

    @Builder.Default
    private List<MemoryEvidenceResponse> evidences = new ArrayList<>();

    @Builder.Default
    private List<PracticeSessionResponse> sessions = new ArrayList<>();
}
