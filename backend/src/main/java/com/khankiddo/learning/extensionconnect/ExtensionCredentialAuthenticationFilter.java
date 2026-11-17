package com.khankiddo.learning.extensionconnect;

import com.khankiddo.learning.model.UserRole;
import com.khankiddo.learning.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 识别 {@code Authorization: Bearer kk_ext_...}，写入与网站 JWT 相同的 {@link AuthenticatedUser}。
 */
@Component
@RequiredArgsConstructor
public class ExtensionCredentialAuthenticationFilter extends OncePerRequestFilter {

    private final ExtensionConnectService extensionConnectService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveExtensionBearer(request).ifPresent(user -> {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                if (UserRole.ADMIN.equals(user.role())) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            });
        }
        filterChain.doFilter(request, response);
    }

    private java.util.Optional<AuthenticatedUser> resolveExtensionBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return java.util.Optional.empty();
        }
        String token = header.substring(7).trim();
        if (!ExtensionConnectTokenSupport.isExtensionToken(token)) {
            return java.util.Optional.empty();
        }
        return extensionConnectService.resolveExtensionToken(token);
    }
}
