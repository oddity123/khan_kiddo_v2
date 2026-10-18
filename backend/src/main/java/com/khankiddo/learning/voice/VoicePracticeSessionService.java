package com.khankiddo.learning.voice;

import com.khankiddo.learning.config.VoicePracticeCapProperties;
import com.khankiddo.learning.dto.voice.VoicePracticeBeginResponse;
import com.khankiddo.learning.dto.voice.VoicePracticeCaptionRequest;
import com.khankiddo.learning.dto.voice.VoicePracticeSessionResponse;
import com.khankiddo.learning.dto.voice.VoicePracticeTranscriptRequest;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.VoicePracticeQuotaExceededException;
import com.khankiddo.learning.mapper.VoicePracticeSessionMapper;
import com.khankiddo.learning.model.VoicePracticeSession;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一场一条的练习记录：额度、开始/结束、已完成字幕。不碰分析管线，不存音频。
 */
@Service
@RequiredArgsConstructor
public class VoicePracticeSessionService {

    static final int RECENT_SAVED_LIMIT = 8;

    private final VoicePracticeSessionMapper mapper;
    private final VoicePracticeCapProperties caps;
    private final Clock clock;

    public synchronized VoicePracticeBeginResponse begin(long userId) {
        LocalDateTime now = now();
        sweepStale(now);
        int remaining = VoicePracticeQuota.remainingSeconds(caps, usage(userId, now));
        if (remaining <= 0) {
            throw new VoicePracticeQuotaExceededException();
        }
        VoicePracticeSession row = VoicePracticeSession.builder()
                .userId(userId)
                .startedAt(now)
                .reservedSeconds(remaining)
                .build();
        mapper.insert(row);
        return VoicePracticeBeginResponse.builder()
                .id(row.getId())
                .grantedSeconds(remaining)
                .build();
    }

    public synchronized int grantedSecondsForConnect(long userId, long sessionId) {
        LocalDateTime now = now();
        sweepStale(now);
        VoicePracticeSession row = requireOwned(userId, sessionId);
        if (row.getEndedAt() != null) {
            throw new BadRequestException("练习会话不存在或已结束");
        }
        int elapsed = elapsedSeconds(row.getStartedAt(), now);
        int reserved = row.getReservedSeconds() == null ? 0 : row.getReservedSeconds();
        return Math.max(0, reserved - elapsed);
    }

    public synchronized void finalizeOpen(long sessionId, boolean stoppedByCap) {
        VoicePracticeSession row = mapper.findById(sessionId).orElse(null);
        if (row == null) {
            return;
        }
        LocalDateTime now = now();
        int elapsed = elapsedSeconds(row.getStartedAt(), now);
        int reserved = row.getReservedSeconds() == null ? 0 : Math.max(row.getReservedSeconds(), 0);
        int wall = reserved == 0 ? elapsed : Math.min(elapsed, reserved);
        boolean cap = stoppedByCap || Boolean.TRUE.equals(row.getStoppedByCap());
        if (row.getEndedAt() == null) {
            mapper.updateClose(sessionId, now, wall, cap);
            return;
        }
        int currentWall = row.getWallDurationSeconds() == null ? 0 : row.getWallDurationSeconds();
        if (wall > currentWall || (cap && !Boolean.TRUE.equals(row.getStoppedByCap()))) {
            mapper.updateClose(sessionId, row.getEndedAt(), Math.max(wall, currentWall), cap);
        }
    }

    public synchronized void abandon(long userId, long sessionId) {
        requireOwned(userId, sessionId);
        mapper.abandonOpen(sessionId, userId, now());
    }

    public synchronized VoicePracticeSessionResponse saveTranscript(
            long userId, long sessionId, VoicePracticeTranscriptRequest request) {
        VoicePracticeSession row = requireOwned(userId, sessionId);
        boolean clientCap = request != null && Boolean.TRUE.equals(request.getStoppedByCap());
        if (row.getEndedAt() == null || clientCap) {
            finalizeOpen(sessionId, clientCap);
            row = requireOwned(userId, sessionId);
        }
        String transcript = VoicePracticeTranscript.format(toCaptions(request));
        boolean cap = clientCap || Boolean.TRUE.equals(row.getStoppedByCap());
        int updated = mapper.updateTranscript(sessionId, userId, transcript, cap);
        if (updated == 0) {
            throw new BadRequestException("练习记录不存在");
        }
        return toResponse(requireOwned(userId, sessionId));
    }

    public VoicePracticeSessionResponse get(long userId, long sessionId) {
        return toResponse(requireOwned(userId, sessionId));
    }

    public List<VoicePracticeSessionResponse> recent(long userId) {
        return mapper.findRecentSaved(userId, RECENT_SAVED_LIMIT).stream()
                .map(this::toResponse)
                .toList();
    }

    private VoicePracticeQuota.Usage usage(long userId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        return new VoicePracticeQuota.Usage(
                mapper.sumUsageSeconds(userId, dayStart, dayEnd),
                mapper.sumUsageSeconds(userId, monthStart, monthEnd),
                mapper.sumUsageSeconds(null, dayStart, dayEnd),
                mapper.sumUsageSeconds(null, monthStart, monthEnd));
    }

    private void sweepStale(LocalDateTime now) {
        mapper.finalizeStaleOpen(now);
    }

    private VoicePracticeSession requireOwned(long userId, long sessionId) {
        VoicePracticeSession row = mapper.findById(sessionId).orElse(null);
        if (row == null || ObjectUtils.isEmpty(row.getUserId()) || row.getUserId() != userId) {
            throw new BadRequestException("练习记录不存在");
        }
        return row;
    }

    private static List<VoicePracticeCaption> toCaptions(VoicePracticeTranscriptRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getLines())) {
            return List.of();
        }
        List<VoicePracticeCaption> captions = new ArrayList<>();
        for (VoicePracticeCaptionRequest line : request.getLines()) {
            if (line == null) {
                continue;
            }
            captions.add(new VoicePracticeCaption(line.getRole(), line.getText()));
        }
        return captions;
    }

    private static int elapsedSeconds(LocalDateTime startedAt, LocalDateTime now) {
        if (startedAt == null || now == null || now.isBefore(startedAt)) {
            return 0;
        }
        long seconds = Duration.between(startedAt, now).getSeconds();
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    private VoicePracticeSessionResponse toResponse(VoicePracticeSession row) {
        return VoicePracticeSessionResponse.builder()
                .id(row.getId())
                .startedAt(row.getStartedAt())
                .endedAt(row.getEndedAt())
                .wallDurationSeconds(row.getWallDurationSeconds() == null ? 0 : row.getWallDurationSeconds())
                .stoppedByCap(Boolean.TRUE.equals(row.getStoppedByCap()))
                .transcriptSaved(Boolean.TRUE.equals(row.getTranscriptSaved()))
                .transcript(row.getTranscript())
                .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
