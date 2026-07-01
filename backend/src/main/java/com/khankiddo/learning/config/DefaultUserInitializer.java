package com.khankiddo.learning.config;

import com.khankiddo.learning.mapper.UserMapper;
import com.khankiddo.learning.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test & !prod")
@RequiredArgsConstructor
public class DefaultUserInitializer {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaultUser() {
        String username = "admin";
        if (userMapper.existsByUsername(username) > 0) {
            log.info("默认用户 {} 已存在，跳过初始化", username);
            return;
        }

        User defaultUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode("admin123"))
                .email("admin@khankiddo.com")
                .enabled(true)
                .build();

        try {
            userMapper.insert(defaultUser);
            log.info("已创建默认管理员账号: {} / admin123（请尽快修改密码）", username);
        } catch (Exception e) {
            log.error("创建默认管理员账号失败", e);
        }
    }
}
