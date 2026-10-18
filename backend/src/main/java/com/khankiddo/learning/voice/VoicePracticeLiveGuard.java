package com.khankiddo.learning.voice;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 连接期间的墙钟硬停。到点只通知并关掉这条套接字，字幕由客户端把已完成句写入练习记录。
 */
@Slf4j
@Component
public class VoicePracticeLiveGuard {

    private final VoicePracticeSessionService sessions;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Armed> armed = new ConcurrentHashMap<>();

    public VoicePracticeLiveGuard(VoicePracticeSessionService sessions) {
        this.sessions = sessions;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "voice-practice-cap");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    /**
     * @return false 表示额度已经用完，调用方不要再去连上游
     */
    public boolean arm(WebSocketSession session, long practiceSessionId, int grantedSeconds) {
        Armed armedSession = new Armed(practiceSessionId);
        armed.put(session.getId(), armedSession);
        if (grantedSeconds <= 0) {
            fireCap(session, armedSession);
            return false;
        }
        armedSession.future = scheduler.schedule(() -> {
            if (armed.containsKey(session.getId())) {
                fireCap(session, armedSession);
            }
        }, grantedSeconds, TimeUnit.SECONDS);
        return true;
    }

    public void onClosed(String wsSessionId) {
        Armed armedSession = armed.remove(wsSessionId);
        if (armedSession == null) {
            return;
        }
        if (armedSession.future != null) {
            armedSession.future.cancel(false);
        }
        sessions.finalizeOpen(armedSession.practiceSessionId, armedSession.cap.get());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private void fireCap(WebSocketSession session, Armed armedSession) {
        if (!armedSession.cap.compareAndSet(false, true)) {
            return;
        }
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(VoicePracticeMessages.QUOTA_EVENT_JSON));
                }
            }
        } catch (IOException e) {
            log.warn("voice quota notice failed session={}: {}", session.getId(), e.toString());
        }
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("quota"));
            }
        } catch (IOException e) {
            log.warn("voice quota close failed session={}: {}", session.getId(), e.toString());
        }
    }

    private static final class Armed {
        private final long practiceSessionId;
        private final AtomicBoolean cap = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> future;

        private Armed(long practiceSessionId) {
            this.practiceSessionId = practiceSessionId;
        }
    }
}
