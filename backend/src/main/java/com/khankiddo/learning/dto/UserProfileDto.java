package com.khankiddo.learning.dto;

import com.khankiddo.learning.model.User;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserProfileDto {

    Long id;
    String username;
    String email;

    public static UserProfileDto from(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
}
