package com.khankiddo.learning.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserWithAnalysisCount extends User {

    private Long analysisCount;
}
