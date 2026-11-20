package com.khankiddo.learning.dto.dailypractice;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPracticeMarkPracticedRequest {

    @NotEmpty(message = "memoryIds 不能为空")
    private List<Long> memoryIds;
}
