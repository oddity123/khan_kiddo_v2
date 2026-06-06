package com.khankiddo.learning.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubmitFeedbackResponse {

    String message;
    Long id;
}
