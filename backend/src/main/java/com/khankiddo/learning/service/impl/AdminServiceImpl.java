package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.admin.AdminAnalysisListResponse;
import com.khankiddo.learning.dto.admin.AdminUserListResponse;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisDetailDto;
import com.khankiddo.learning.dto.conversation.ConversationAnalysisListResponse;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.mapper.UserMapper;
import com.khankiddo.learning.model.UserWithAnalysisCount;
import com.khankiddo.learning.model.UserRole;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.service.AdminService;
import com.khankiddo.learning.service.conversation.ConversationAnalysisService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final ConversationAnalysisService conversationAnalysisService;

    @Override
    public AdminUserListResponse listUsers(int page, int size, String keyword,
                                           Integer minAnalysisCount, Integer maxAnalysisCount) {
        SecurityUtils.requireAdmin();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Integer safeMinCount = normalizeAnalysisCount(minAnalysisCount, "对话次数下限");
        Integer safeMaxCount = normalizeAnalysisCount(maxAnalysisCount, "对话次数上限");
        if (ObjectUtils.isNotEmpty(safeMinCount) && ObjectUtils.isNotEmpty(safeMaxCount) && safeMinCount > safeMaxCount) {
            throw new BadRequestException("对话次数下限不能大于上限");
        }

        List<UserWithAnalysisCount> users = userMapper.findAllForAdmin(
                trimmedKeyword, safeMinCount, safeMaxCount, safeSize, offset);
        long total = userMapper.countAllForAdmin(trimmedKeyword, safeMinCount, safeMaxCount);

        List<AdminUserListResponse.UserRow> rows = users.stream()
                .map(user -> AdminUserListResponse.UserRow.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(UserRole.ADMIN.equals(user.getRole()) ? UserRole.ADMIN : UserRole.USER)
                        .enabled(user.getEnabled())
                        .createdAt(user.getCreatedAt())
                        .analysisCount(ObjectUtils.defaultIfNull(user.getAnalysisCount(), 0L))
                        .build())
                .toList();

        return AdminUserListResponse.builder().total(total).records(rows).build();
    }

    @Override
    public ConversationAnalysisListResponse listUserAnalyses(Long userId, int page, int size, String keyword) {
        SecurityUtils.requireAdmin();
        if (ObjectUtils.isEmpty(userId)) {
            throw new BadRequestException("用户不存在");
        }
        userMapper.findById(userId).orElseThrow(() -> new BadRequestException("用户不存在"));
        return conversationAnalysisService.listForUser(userId, page, size, keyword);
    }

    @Override
    public AdminAnalysisListResponse listAnalyses(int page, int size, String keyword, String username) {
        SecurityUtils.requireAdmin();
        return conversationAnalysisService.listAllAsAdmin(page, size, keyword, username);
    }

    @Override
    public ConversationAnalysisDetailDto getAnalysisDetail(String analysisId) {
        SecurityUtils.requireAdmin();
        return conversationAnalysisService.getDetailAsAdmin(analysisId);
    }

    private Integer normalizeAnalysisCount(Integer value, String label) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        if (value < 0) {
            throw new BadRequestException(label + "不能为负数");
        }
        return value;
    }
}
