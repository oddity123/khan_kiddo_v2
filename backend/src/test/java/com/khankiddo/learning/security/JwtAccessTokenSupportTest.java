package com.khankiddo.learning.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenSupportTest {

    @Test
    void resolveFromServletRequest_readsQueryParam() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(JwtAccessTokenSupport.ACCESS_TOKEN_QUERY_PARAM, "  abc.def  ");
        assertThat(JwtAccessTokenSupport.resolveFromServletRequest(request)).isEqualTo("abc.def");
    }

    @Test
    void resolveFromQueryString_decodesUrlEncodedToken() {
        String token = JwtAccessTokenSupport.resolveFromQueryString(
                "foo=1&access_token=a%2Fb.c&bar=2");
        assertThat(token).isEqualTo("a/b.c");
    }

    @Test
    void resolveFromQueryString_returnsNullWhenMissing() {
        assertThat(JwtAccessTokenSupport.resolveFromQueryString("foo=1")).isNull();
        assertThat(JwtAccessTokenSupport.resolveFromQueryString(null)).isNull();
        assertThat(JwtAccessTokenSupport.resolveFromQueryString("")).isNull();
    }
}
