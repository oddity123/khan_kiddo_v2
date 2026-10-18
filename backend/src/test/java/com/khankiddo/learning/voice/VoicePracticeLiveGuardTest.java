package com.khankiddo.learning.voice;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoicePracticeLiveGuardTest {

    @Test
    void zeroGrantNotifiesAndCloseFinalizesAsCap() throws Exception {
        VoicePracticeSessionService sessions = mock(VoicePracticeSessionService.class);
        VoicePracticeLiveGuard guard = new VoicePracticeLiveGuard(sessions);
        try {
            WebSocketSession session = openSession("s1");
            assertThat(guard.arm(session, 9L, 0)).isFalse();
            verify(session).sendMessage(argThat(message ->
                    message instanceof TextMessage text
                            && text.getPayload().contains("proxy.quota_exhausted")
                            && text.getPayload().contains(VoicePracticeMessages.QUOTA_EXHAUSTED)));
            verify(session).close(any());
            guard.onClosed("s1");
            verify(sessions).finalizeOpen(9L, true);
        } finally {
            guard.shutdown();
        }
    }

    @Test
    void normalCloseFinalizesWithoutCap() throws Exception {
        VoicePracticeSessionService sessions = mock(VoicePracticeSessionService.class);
        VoicePracticeLiveGuard guard = new VoicePracticeLiveGuard(sessions);
        try {
            WebSocketSession session = openSession("s2");
            assertThat(guard.arm(session, 3L, 30)).isTrue();
            guard.onClosed("s2");
            verify(sessions).finalizeOpen(3L, false);
        } finally {
            guard.shutdown();
        }
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
