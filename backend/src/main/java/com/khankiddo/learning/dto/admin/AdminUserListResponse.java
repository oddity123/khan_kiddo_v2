package com.khankiddo.learning.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListResponse {

    private long total;
    private List<UserRow> records;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRow {
        private Long id;
        private String username;
        private String email;
        private String role;
        private Boolean enabled;
        private LocalDateTime createdAt;
        /** 对话分析记录数 */
        private Long analysisCount;
    }
}
