package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.HomePageResponse;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页数据 API。已登录时根据 JWT 解析当前用户 ID；未登录返回未认证态。
 */
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public HomePageResponse home() {
        return homeService.getHomePage(SecurityUtils.getCurrentUserId());
    }
}
