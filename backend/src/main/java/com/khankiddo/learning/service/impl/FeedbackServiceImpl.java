package com.khankiddo.learning.service.impl;

import com.khankiddo.learning.dto.SubmitFeedbackRequest;
import com.khankiddo.learning.dto.SubmitFeedbackResponse;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.mapper.UserFeedbackMapper;
import com.khankiddo.learning.model.UserFeedback;
import com.khankiddo.learning.security.SecurityUtils;
import com.khankiddo.learning.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final UserFeedbackMapper userFeedbackMapper;

    @Override
    public SubmitFeedbackResponse submit(SubmitFeedbackRequest request) {
        String title = request.getTitle().trim();
        String content = request.getContent().trim();
        String email = normalizeEmail(request.getEmail());

        UserFeedback feedback = UserFeedback.builder()
                .userId(SecurityUtils.getCurrentUserId())
                .title(title)
                .email(email)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        int rows = userFeedbackMapper.insert(feedback);
        if (rows <= 0) {
            throw new BadRequestException("提交失败，请稍后重试");
        }

        log.info("用户反馈已保存 - id: {}, userId: {}, title: {}", feedback.getId(), feedback.getUserId(), title);
        return SubmitFeedbackResponse.builder()
                .id(feedback.getId())
                .message("感谢你的反馈！我们会尽快查看并持续优化产品。")
                .build();
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim();
    }
}
