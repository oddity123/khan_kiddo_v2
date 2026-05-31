package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.*;
import com.khankiddo.learning.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 API：JWT Bearer（{@code Authorization: Bearer &lt;token&gt;}）。
 * 前端将 token 存入 localStorage；登出由前端清除 token，服务端无状态。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public UserProfileDto me() {
        return authService.getCurrentUserProfile();
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        return Map.of("message", "已退出登录");
    }
}
