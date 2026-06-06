package com.khankiddo.learning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFeedback {

    private Long id;
    private Long userId;
    private String title;
    private String email;
    private String content;
    private LocalDateTime createdAt;
}
