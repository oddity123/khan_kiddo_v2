package com.khankiddo.learning.extensionconnect;

import com.khankiddo.learning.config.ExtensionConnectProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.ForbiddenException;
import com.khankiddo.learning.exception.UnauthorizedException;
import com.khankiddo.learning.extensionconnect.dto.ConnectSessionResponse;
import com.khankiddo.learning.extensionconnect.dto.CreateConnectSessionRequest;
import com.khankiddo.learning.extensionconnect.dto.ExchangeConnectRequest;
import com.khankiddo.learning.extensionconnect.dto.ExchangeConnectResponse;
import com.khankiddo.learning.extensionconnect.dto.ExtensionConnectConfigResponse;
import com.khankiddo.learning.extensionconnect.dto.ExtensionConnectMeResponse;
import com.khankiddo.learning.funnel.FunnelEventName;
import com.khankiddo.learning.funnel.FunnelEventService;
import com.khankiddo.learning.mapper.UserMapper;
import com.khankiddo.learning.model.User;
import com.khankiddo.learning.model.UserRole;
import com.khankiddo.learning.security.AuthenticatedUser;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtensionConnectService {

    private final ExtensionConnectProperties properties;
    private final ExtensionConnectSessionMapper sessionMapper;
    private final ExtensionCredentialMapper credentialMapper;
    private final UserMapper userMapper;
    private final FunnelEventService funnelEventService;
    private final Clock clock;

    public ExtensionConnectConfigResponse config() {
        return ExtensionConnectConfigResponse.builder()
                .enabled(properties.isEnabled())
                .sessionTtlMinutes(properties.getSessionTtlMinutes())
                .credentialTtlDays(properties.getCredentialTtlDays())
                .pollIntervalMs(properties.getPollIntervalMs())
                .pollTimeoutMs(properties.getPollTimeoutMs())
                .build();
    }

    public void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ForbiddenException("扩展账号连接暂未开放");
        }
    }

    @Transactional
    public ConnectSessionResponse createSession(CreateConnectSessionRequest request) {
        requireEnabled();
        String challenge = request.getCodeChallenge().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        ExtensionConnectSession session = ExtensionConnectSession.builder()
                .id(UUID.randomUUID().toString())
                .codeChallenge(challenge)
                .status(ExtensionConnectSessionStatus.PENDING)
                .expiresAt(now.plusMinutes(properties.getSessionTtlMinutes()))
                .createdAt(now)
                .build();
        sessionMapper.insert(session);
        funnelEventService.recordInternal(null, FunnelEventName.ext_connect_started);
        return toSessionResponse(session);
    }

    public ConnectSessionResponse getSession(String sessionId) {
        requireEnabled();
        ExtensionConnectSession session = requireSession(sessionId);
        return toSessionResponse(refreshExpired(session));
    }

    @Transactional
    public ConnectSessionResponse authorize(String sessionId) {
        requireEnabled();
        Long userId = SecurityUtils.requireUserId();
        ExtensionConnectSession session = refreshExpired(requireSession(sessionId));
        if (ExtensionConnectSessionStatus.DENIED.equals(session.getStatus())
                || ExtensionConnectSessionStatus.EXPIRED.equals(session.getStatus())) {
            throw new BadRequestException("连接会话已失效，请在扩展中重新发起");
        }
        if (ExtensionConnectSessionStatus.EXCHANGED.equals(session.getStatus())) {
            throw new BadRequestException("连接会话已完成兑换");
        }
        if (ExtensionConnectSessionStatus.AUTHORIZED.equals(session.getStatus())) {
            if (!userId.equals(session.getUserId())) {
                throw new ForbiddenException("该连接会话已由其他账号授权");
            }
            return toSessionResponse(session);
        }
        if (!ExtensionConnectSessionStatus.PENDING.equals(session.getStatus())) {
            throw new BadRequestException("连接会话状态无效");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = sessionMapper.markAuthorized(
                session.getId(), userId, now, ExtensionConnectSessionStatus.PENDING);
        if (updated != 1) {
            throw new BadRequestException("授权失败，请重试");
        }
        funnelEventService.recordInternal(userId, FunnelEventName.ext_connect_authorized);
        session.setStatus(ExtensionConnectSessionStatus.AUTHORIZED);
        session.setUserId(userId);
        session.setAuthorizedAt(now);
        return toSessionResponse(session);
    }

    @Transactional
    public ConnectSessionResponse deny(String sessionId) {
        requireEnabled();
        SecurityUtils.requireUserId();
        ExtensionConnectSession session = refreshExpired(requireSession(sessionId));
        if (ExtensionConnectSessionStatus.EXCHANGED.equals(session.getStatus())) {
            throw new BadRequestException("连接已完成，无法拒绝");
        }
        if (ExtensionConnectSessionStatus.DENIED.equals(session.getStatus())
                || ExtensionConnectSessionStatus.EXPIRED.equals(session.getStatus())) {
            return toSessionResponse(session);
        }
        if (ExtensionConnectSessionStatus.AUTHORIZED.equals(session.getStatus())
                && ObjectUtils.isNotEmpty(session.getUserId())
                && !session.getUserId().equals(SecurityUtils.requireUserId())) {
            throw new ForbiddenException("无权拒绝该连接会话");
        }
        if (ExtensionConnectSessionStatus.PENDING.equals(session.getStatus())
                || ExtensionConnectSessionStatus.AUTHORIZED.equals(session.getStatus())) {
            sessionMapper.markDenied(session.getId(), session.getStatus());
            session.setStatus(ExtensionConnectSessionStatus.DENIED);
            funnelEventService.recordInternal(SecurityUtils.getCurrentUserId(), FunnelEventName.ext_connect_failed);
        }
        return toSessionResponse(session);
    }

    @Transactional
    public ExchangeConnectResponse exchange(ExchangeConnectRequest request) {
        requireEnabled();
        ExtensionConnectSession session = refreshExpired(requireSession(request.getSessionId().trim()));
        String status = session.getStatus();
        if (ExtensionConnectSessionStatus.PENDING.equals(status)) {
            return ExchangeConnectResponse.builder().status(ExtensionConnectSessionStatus.PENDING).build();
        }
        if (ExtensionConnectSessionStatus.DENIED.equals(status)
                || ExtensionConnectSessionStatus.EXPIRED.equals(status)) {
            funnelEventService.recordInternal(null, FunnelEventName.ext_connect_failed);
            throw new BadRequestException("连接会话已失效，请重新发起");
        }
        if (ExtensionConnectSessionStatus.EXCHANGED.equals(status)) {
            throw new BadRequestException("连接凭证已兑换，请勿重复请求");
        }
        if (!ExtensionConnectSessionStatus.AUTHORIZED.equals(status)) {
            throw new BadRequestException("连接会话状态无效");
        }
        if (!ExtensionConnectTokenSupport.matchesPkce(request.getCodeVerifier(), session.getCodeChallenge())) {
            funnelEventService.recordInternal(session.getUserId(), FunnelEventName.ext_connect_failed);
            throw new UnauthorizedException("校验失败，请重新连接");
        }
        if (ObjectUtils.isEmpty(session.getUserId())) {
            throw new BadRequestException("连接会话缺少用户绑定");
        }
        User user = userMapper.findById(session.getUserId())
                .orElseThrow(() -> new BadRequestException("用户不存在"));
        LocalDateTime now = LocalDateTime.now(clock);
        int marked = sessionMapper.markExchanged(
                session.getId(), now, ExtensionConnectSessionStatus.AUTHORIZED);
        if (marked != 1) {
            throw new BadRequestException("兑换冲突，请重试");
        }
        String token = ExtensionConnectTokenSupport.newOpaqueToken();
        ExtensionCredential credential = ExtensionCredential.builder()
                .userId(user.getId())
                .tokenHash(ExtensionConnectTokenSupport.sha256Hex(token))
                .expiresAt(now.plusDays(properties.getCredentialTtlDays()))
                .createdAt(now)
                .build();
        credentialMapper.insert(credential);
        funnelEventService.recordInternal(user.getId(), FunnelEventName.ext_connect_exchanged);
        return ExchangeConnectResponse.builder()
                .status(ExtensionConnectSessionStatus.EXCHANGED)
                .token(token)
                .username(user.getUsername())
                .expiresAt(credential.getExpiresAt())
                .build();
    }

    public ExtensionConnectMeResponse me() {
        requireEnabled();
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        // 当前请求若由扩展凭证认证，Filter 已设置；列出用户活跃凭证中最新一条
        var active = credentialMapper.findActiveByUserId(user.id(), now);
        if (active.isEmpty()) {
            return ExtensionConnectMeResponse.builder()
                    .connected(false)
                    .username(user.username())
                    .build();
        }
        ExtensionCredential cred = active.getFirst();
        return ExtensionConnectMeResponse.builder()
                .connected(true)
                .username(user.username())
                .credentialId(cred.getId())
                .expiresAt(cred.getExpiresAt())
                .build();
    }

    @Transactional
    public void revokeCurrent() {
        requireEnabled();
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now(clock);
        int n = credentialMapper.revokeAllActiveByUserId(user.id(), now);
        if (n > 0) {
            funnelEventService.recordInternal(user.id(), FunnelEventName.ext_connect_revoked);
        }
    }

    public Optional<AuthenticatedUser> resolveExtensionToken(String rawToken) {
        if (!ExtensionConnectTokenSupport.isExtensionToken(rawToken)) {
            return Optional.empty();
        }
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String hash = ExtensionConnectTokenSupport.sha256Hex(rawToken);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<ExtensionCredential> cred = credentialMapper.findActiveByTokenHash(hash, now);
        if (cred.isEmpty()) {
            return Optional.empty();
        }
        ExtensionCredential credential = cred.get();
        User user = userMapper.findById(credential.getUserId()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return Optional.empty();
        }
        credentialMapper.touchLastUsed(credential.getId(), now);
        String role = StringUtils.hasText(user.getRole()) ? user.getRole() : UserRole.USER;
        return Optional.of(new AuthenticatedUser(user.getId(), user.getUsername(), role));
    }

    private ExtensionConnectSession requireSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BadRequestException("sessionId 无效");
        }
        return sessionMapper.findById(sessionId.trim())
                .orElseThrow(() -> new BadRequestException("连接会话不存在"));
    }

    private ExtensionConnectSession refreshExpired(ExtensionConnectSession session) {
        if (ExtensionConnectSessionStatus.EXPIRED.equals(session.getStatus())
                || ExtensionConnectSessionStatus.EXCHANGED.equals(session.getStatus())
                || ExtensionConnectSessionStatus.DENIED.equals(session.getStatus())) {
            return session;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) {
            session.setStatus(ExtensionConnectSessionStatus.EXPIRED);
        }
        return session;
    }

    private static ConnectSessionResponse toSessionResponse(ExtensionConnectSession session) {
        return ConnectSessionResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
