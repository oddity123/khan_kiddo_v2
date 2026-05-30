package com.khankiddo.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomePageResponse {

    private String title;
    private String description;
    private boolean authenticated;
    private AnalysisDashboardStats analysisStats;
}
