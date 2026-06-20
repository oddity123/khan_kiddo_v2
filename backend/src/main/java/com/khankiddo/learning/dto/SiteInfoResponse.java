package com.khankiddo.learning.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SiteInfoResponse {

    String icpNumber;
    String icpUrl;
    String psbNumber;
    String psbUrl;
}
