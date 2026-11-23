package com.khankiddo.learning.controller;

import com.khankiddo.learning.dailypractice.DailyPracticeService;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeMarkPracticedRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeMarkPracticedResponse;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendRequest;
import com.khankiddo.learning.dto.dailypractice.DailyPracticeRecommendResponse;
import com.khankiddo.learning.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practice/daily")
@RequiredArgsConstructor
public class DailyPracticeController {

    private final DailyPracticeService dailyPracticeService;

    @PostMapping("/recommend")
    public DailyPracticeRecommendResponse recommend(
            @RequestBody(required = false) DailyPracticeRecommendRequest request) {
        return dailyPracticeService.recommend(SecurityUtils.requireUserId(), request);
    }

    @PostMapping("/mark-practiced")
    public DailyPracticeMarkPracticedResponse markPracticed(
            @Valid @RequestBody DailyPracticeMarkPracticedRequest request) {
        return dailyPracticeService.markPracticed(SecurityUtils.requireUserId(), request);
    }
}
