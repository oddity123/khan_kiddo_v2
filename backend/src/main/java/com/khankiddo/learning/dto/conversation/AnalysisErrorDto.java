package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisErrorDto {

    private String pointId;
    private String type;
    private String point;
    private String errorLevel;
    private String familyId;
    private String channel;
}
