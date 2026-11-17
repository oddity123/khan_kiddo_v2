package com.khankiddo.learning.extensionconnect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionConnectTokenSupportTest {

    @Test
    void opaqueTokenHasPrefixAndHashes() {
        String token = ExtensionConnectTokenSupport.newOpaqueToken();
        assertThat(token).startsWith(ExtensionConnectTokenSupport.TOKEN_PREFIX);
        assertThat(ExtensionConnectTokenSupport.isExtensionToken(token)).isTrue();
        assertThat(ExtensionConnectTokenSupport.sha256Hex(token)).hasSize(64);
    }

    @Test
    void pkceS256RoundTrip() {
        String verifier = ExtensionConnectTokenSupport.newCodeVerifier();
        String challenge = ExtensionConnectTokenSupport.pkceS256Challenge(verifier);
        assertThat(ExtensionConnectTokenSupport.matchesPkce(verifier, challenge)).isTrue();
        assertThat(ExtensionConnectTokenSupport.matchesPkce(verifier + "x", challenge)).isFalse();
    }
}
