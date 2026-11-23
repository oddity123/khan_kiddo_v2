package com.khankiddo.learning.dto.dailypractice;

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
public class DailyPracticeMarkPracticedResponse {

    @Builder.Default
    private List<Long> sessionIds = new ArrayList<>();
}
