package com.khankiddo.learning.service;

import com.khankiddo.learning.dto.AnalysisDashboardStats;
import com.khankiddo.learning.dto.HomePageResponse;

public interface HomeService {

    HomePageResponse getHomePage(Long userId);
}
