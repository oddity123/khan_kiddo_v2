package com.khankiddo.learning.memory.service;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * 薄弱点记忆生命周期状态机：NEW → ACTIVE → PRACTICING → MASTERED / ARCHIVED 及复发重激活。
 */
@Service
@RequiredArgsConstructor
public class MemoryLifecycleService {

    private static final Set<MemoryStatus> MANUAL_ALLOWED_STATUSES = EnumSet.of(
            MemoryStatus.ACTIVE,
            MemoryStatus.MASTERED,
            MemoryStatus.ARCHIVED);

    private final WeaknessMemoryProperties properties;

    public void onHitRecorded(WeaknessMemory memory, int recentHitCount, LocalDateTime now) {
        MemoryStatus current = MemoryStatus.fromString(memory.getStatus());
        switch (current) {
            case NEW -> {
                if (recentHitCount >= Math.max(1, properties.getConfirmMinHits())) {
                    memory.setStatus(MemoryStatus.ACTIVE.name());
                    memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalActiveDays())));
                }
            }
            case MASTERED, ARCHIVED -> {
                memory.setStatus(MemoryStatus.ACTIVE.name());
                memory.setConsecutiveCorrect(0);
                memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalActiveDays())));
            }
            case ACTIVE, PRACTICING -> {
                if (memory.getNextReviewAt() == null) {
                    int days = current == MemoryStatus.PRACTICING
                            ? properties.getReviewIntervalPracticingDays()
                            : properties.getReviewIntervalActiveDays();
                    memory.setNextReviewAt(now.plusDays(Math.max(1, days)));
                }
            }
        }
    }

    public void onPracticeStarted(WeaknessMemory memory, LocalDateTime now) {
        MemoryStatus current = MemoryStatus.fromString(memory.getStatus());
        if (current == MemoryStatus.NEW
                || current == MemoryStatus.ACTIVE
                || current == MemoryStatus.MASTERED) {
            memory.setStatus(MemoryStatus.PRACTICING.name());
        }
        memory.setLastPracticedAt(now);
        memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalPracticingDays())));
    }

    public void onMasteryEvaluated(WeaknessMemory memory, boolean qualifiedForMastery, LocalDateTime now) {
        MemoryStatus current = MemoryStatus.fromString(memory.getStatus());
        if (current == MemoryStatus.ARCHIVED) {
            return;
        }
        if (qualifiedForMastery) {
            memory.setStatus(MemoryStatus.MASTERED.name());
            memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalMasteredDays())));
            return;
        }
        if (current == MemoryStatus.MASTERED) {
            memory.setStatus(MemoryStatus.ACTIVE.name());
            memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalActiveDays())));
        }
    }

    public void applyManualStatus(WeaknessMemory memory, MemoryStatus targetStatus, LocalDateTime now) {
        if (targetStatus == null || !MANUAL_ALLOWED_STATUSES.contains(targetStatus)) {
            throw new BadRequestException("仅支持切换为 ACTIVE、MASTERED 或 ARCHIVED");
        }
        memory.setStatus(targetStatus.name());
        switch (targetStatus) {
            case ARCHIVED -> memory.setNextReviewAt(null);
            case ACTIVE -> memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalActiveDays())));
            case MASTERED -> memory.setNextReviewAt(now.plusDays(Math.max(1, properties.getReviewIntervalMasteredDays())));
            default -> {
            }
        }
    }
}
