package com.khankiddo.learning.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryListResponse {

    @Builder.Default
    private List<MemoryItemResponse> items = new ArrayList<>();

    @Builder.Default
    private Map<String, Integer> countsByStatus = new LinkedHashMap<>();
}
