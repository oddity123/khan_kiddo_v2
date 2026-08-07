package com.khankiddo.learning.conversation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.GuestQuotaExceededException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游客免费分析配额（Cookie {@code guest_id} + 进程内计数，单实例有效）。
 * <p>
 * 计数用 Caffeine 限制容量并按 Cookie 有效期过期，避免 {@code guest_id} 无限堆积。
 */
@Service
public class GuestAnalysisQuotaService {

    /** 防刷：单实例最多保留的 guest 计数条目 */
    private static final long MAX_GUEST_ENTRIES = 50_000L;

    private final ConversationAnalysisProperties properties;
    private final Cache<String, AtomicInteger> usageByGuestId;

    public GuestAnalysisQuotaService(ConversationAnalysisProperties properties) {
        this.properties = properties;
        Duration expireAfter = Duration.ofDays(Math.max(1, properties.getGuestCookieMaxAgeDays()));
        this.usageByGuestId = Caffeine.newBuilder()
                .maximumSize(MAX_GUEST_ENTRIES)
                .expireAfterAccess(expireAfter)
                .build();
    }

    public String resolveOrCreateGuestId(HttpServletRequest request, HttpServletResponse response) {
        String existing = readGuestId(request);
        if (StringUtils.hasText(existing)) {
            return existing.trim();
        }
        String guestId = UUID.randomUUID().toString().replace("-", "");
        writeGuestCookie(request, response, guestId);
        return guestId;
    }

    public String readGuestId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = properties.getGuestCookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue().trim();
            }
        }
        return null;
    }

    /**
     * 预占一次配额（请求受理时调用）；已满则抛 {@link GuestQuotaExceededException}。
     * 流水线失败时应 {@link #refund(String)}。
     */
    public void reserveOrThrow(String guestId) {
        int limit = Math.max(0, properties.getGuestFreeAnalyzeLimit());
        AtomicInteger counter = usageByGuestId.get(guestId, ignored -> new AtomicInteger(0));
        while (true) {
            int used = counter.get();
            if (used >= limit) {
                throw new GuestQuotaExceededException("免费体验次数已用完，请登录后继续分析");
            }
            if (counter.compareAndSet(used, used + 1)) {
                return;
            }
        }
    }

    /**
     * 分析失败时退还预占次数（客户端中途断开且已跑完 LLM 的情况不退，由调用方决定）。
     */
    public void refund(String guestId) {
        if (!StringUtils.hasText(guestId)) {
            return;
        }
        AtomicInteger counter = usageByGuestId.getIfPresent(guestId);
        if (counter == null) {
            return;
        }
        while (true) {
            int used = counter.get();
            if (used <= 0) {
                return;
            }
            if (counter.compareAndSet(used, used - 1)) {
                return;
            }
        }
    }

    public GuestQuotaSnapshot snapshot(HttpServletRequest request) {
        int limit = Math.max(0, properties.getGuestFreeAnalyzeLimit());
        String guestId = readGuestId(request);
        int used = 0;
        if (StringUtils.hasText(guestId)) {
            AtomicInteger counter = usageByGuestId.getIfPresent(guestId);
            if (counter != null) {
                used = Math.min(limit, Math.max(0, counter.get()));
            }
        }
        return new GuestQuotaSnapshot(limit, used, Math.max(0, limit - used));
    }

    private void writeGuestCookie(HttpServletRequest request, HttpServletResponse response, String guestId) {
        Duration maxAge = Duration.ofDays(Math.max(1, properties.getGuestCookieMaxAgeDays()));
        ResponseCookie cookie = ResponseCookie.from(properties.getGuestCookieName(), guestId)
                .httpOnly(true)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .secure(request.isSecure())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public record GuestQuotaSnapshot(int limit, int used, int remaining) {
    }
}
