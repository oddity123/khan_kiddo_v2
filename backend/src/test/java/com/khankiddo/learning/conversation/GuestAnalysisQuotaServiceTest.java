package com.khankiddo.learning.conversation;

import com.khankiddo.learning.config.ConversationAnalysisProperties;
import com.khankiddo.learning.exception.GuestQuotaExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class GuestAnalysisQuotaServiceTest {

    private GuestAnalysisQuotaService service;

    @BeforeEach
    void setUp() {
        ConversationAnalysisProperties properties = new ConversationAnalysisProperties();
        properties.setGuestFreeAnalyzeLimit(3);
        properties.setGuestCookieName("kk_guest_id");
        properties.setGuestCookieMaxAgeDays(365);
        service = new GuestAnalysisQuotaService(properties);
    }

    @Test
    void createsCookieAndAllowsThreeReserves() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String guestId = service.resolveOrCreateGuestId(request, response);
        assertNotNull(guestId);
        assertTrue(response.getHeader("Set-Cookie").contains("kk_guest_id="));

        service.reserveOrThrow(guestId);
        service.reserveOrThrow(guestId);
        service.reserveOrThrow(guestId);

        assertEquals(0, service.snapshot(requestWithGuest(guestId)).remaining());
        assertThrows(GuestQuotaExceededException.class, () -> service.reserveOrThrow(guestId));
    }

    @Test
    void refundRestoresOneSlot() {
        String guestId = "guest-refund-test";
        service.reserveOrThrow(guestId);
        service.reserveOrThrow(guestId);
        service.reserveOrThrow(guestId);
        assertThrows(GuestQuotaExceededException.class, () -> service.reserveOrThrow(guestId));

        service.refund(guestId);
        assertEquals(1, service.snapshot(requestWithGuest(guestId)).remaining());
        service.reserveOrThrow(guestId);
        assertEquals(0, service.snapshot(requestWithGuest(guestId)).remaining());
    }

    private MockHttpServletRequest requestWithGuest(String guestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("kk_guest_id", guestId));
        return request;
    }
}
