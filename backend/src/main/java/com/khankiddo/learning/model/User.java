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
public class User {

    private Long id;
    private String username;
    private String password;
    private String email;
    private Boolean enabled;
    /** {@link UserRole#USER} 或 {@link UserRole#ADMIN} */
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
