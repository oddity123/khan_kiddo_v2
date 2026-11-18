package com.khankiddo.learning.analysistask.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalysisTaskLookupResponse {
    boolean found;
    AnalysisTaskResponse task;
}
