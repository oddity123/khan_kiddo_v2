package com.khankiddo.learning.weaknessmemory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeaknessMemoryConsolidationResult {

    private List<OperationDto> operations = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationDto {
        private String op;
        private Long memoryId;
        private String title;
        private String anchorHabitKey;
        private String anchorPointId;
    }
}
