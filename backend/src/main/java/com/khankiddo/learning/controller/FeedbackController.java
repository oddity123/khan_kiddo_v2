package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.SubmitFeedbackRequest;
import com.khankiddo.learning.dto.SubmitFeedbackResponse;
import com.khankiddo.learning.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public SubmitFeedbackResponse submit(@Valid @RequestBody SubmitFeedbackRequest request) {
        return feedbackService.submit(request);
    }
}
