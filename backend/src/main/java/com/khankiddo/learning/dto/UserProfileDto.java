package com.khankiddo.learning.dto;

import com.khankiddo.learning.model.User;
import com.khankiddo.learning.model.UserRole;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserProfileDto {

    Long id;
    String username;
    String email;
    String role;

    public static UserProfileDto from(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(resolveRole(user.getRole()))
                .build();
    }

    private static String resolveRole(String role) {
        return UserRole.ADMIN.equals(role) ? UserRole.ADMIN : UserRole.USER;
    }
}
