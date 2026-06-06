package com.khankiddo.learning.service;

import com.khankiddo.learning.dto.SubmitFeedbackRequest;
import com.khankiddo.learning.dto.SubmitFeedbackResponse;

public interface FeedbackService {

    SubmitFeedbackResponse submit(SubmitFeedbackRequest request);
}
