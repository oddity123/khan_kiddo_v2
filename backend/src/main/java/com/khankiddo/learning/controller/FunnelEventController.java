package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.funnel.FunnelEventRequest;
import com.khankiddo.learning.funnel.FunnelEventService;
import com.khankiddo.learning.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 漏斗计数。游客分析也要能记；过期 token 不当成游客。
 */
@RestController
@RequestMapping("/api/funnel")
@RequiredArgsConstructor
public class FunnelEventController {

    private final FunnelEventService funnelEvents;

    @PostMapping("/events")
    public Map<String, Boolean> record(HttpServletRequest request, @Valid @RequestBody FunnelEventRequest body) {
        SecurityUtils.rejectStaleBearer(request);
        funnelEvents.record(SecurityUtils.getCurrentUserId(), body);
        return Map.of("ok", true);
    }
}
