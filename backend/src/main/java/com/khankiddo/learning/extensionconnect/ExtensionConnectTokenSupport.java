package com.khankiddo.learning.extensionconnect;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 扩展 opaque 凭证与 PKCE S256 工具。凭证明文前缀 {@link #TOKEN_PREFIX}。
 */
public final class ExtensionConnectTokenSupport {

    public static final String TOKEN_PREFIX = "kk_ext_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private ExtensionConnectTokenSupport() {
    }

    public static boolean isExtensionToken(String token) {
        return StringUtils.hasText(token) && token.startsWith(TOKEN_PREFIX);
    }

    public static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** PKCE S256：BASE64URL(SHA256(verifier))，无填充。 */
    public static String pkceS256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matchesPkce(String codeVerifier, String storedChallenge) {
        if (!StringUtils.hasText(codeVerifier) || !StringUtils.hasText(storedChallenge)) {
            return false;
        }
        return storedChallenge.equals(pkceS256Challenge(codeVerifier.trim()));
    }

    public static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
