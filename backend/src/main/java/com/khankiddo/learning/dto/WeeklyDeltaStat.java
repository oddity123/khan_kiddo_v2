package com.khankiddo.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyDeltaStat {

    private long current;
    private long previous;
    private long delta;
    private int percent;
}
