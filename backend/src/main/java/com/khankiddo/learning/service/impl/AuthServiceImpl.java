package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.*;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.UnauthorizedException;
import com.khankiddo.learning.mapper.UserMapper;
import com.khankiddo.learning.model.User;
import com.khankiddo.learning.security.AuthenticatedUser;
import com.khankiddo.learning.security.JwtService;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new UnauthorizedException("账号已禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserProfileDto.from(user))
                .build();
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null;

        if (StringUtils.hasText(email) && !email.matches("^[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            throw new BadRequestException("邮箱格式不正确");
        }

        if (userMapper.existsByUsername(username) > 0) {
            throw new BadRequestException("用户名已存在");
        }

        User newUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .email(email)
                .enabled(true)
                .build();
        userMapper.insert(newUser);

        log.info("成功注册新用户: {}", username);
        return RegisterResponse.builder()
                .message("注册成功，请登录")
                .user(UserProfileDto.from(newUser))
                .build();
    }

    @Override
    public UserProfileDto getCurrentUserProfile() {
        AuthenticatedUser current = SecurityUtils.getCurrentUser();
        if (ObjectUtils.isEmpty(current) || ObjectUtils.isEmpty(current.id())) {
            throw new UnauthorizedException("未登录");
        }

        User user = userMapper.findById(current.id())
                .orElseThrow(() -> new UnauthorizedException("未登录"));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new UnauthorizedException("账号已禁用");
        }

        if (!StringUtils.hasText(user.getUsername())
                || !user.getUsername().equals(current.username())) {
            throw new UnauthorizedException("未登录");
        }

        return UserProfileDto.from(user);
    }
}
