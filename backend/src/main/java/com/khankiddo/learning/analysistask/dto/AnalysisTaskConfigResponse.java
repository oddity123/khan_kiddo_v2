package com.khankiddo.learning.analysistask.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalysisTaskConfigResponse {
    boolean enabled;
    long pollIntervalMs;
    long pollTimeoutMs;
}
