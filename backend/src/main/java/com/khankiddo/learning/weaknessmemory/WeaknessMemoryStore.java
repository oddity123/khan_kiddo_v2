package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.UserWeaknessMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 薄弱点记忆事务落库：校验 memoryId 归属后执行 CREATE/MERGE，并做活跃池淘汰。
 * 不落句子或教学文案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeaknessMemoryStore {

    private final UserWeaknessMemoryMapper memoryMapper;
    private final WeaknessMemoryCapacitySupport capacitySupport;

    @Transactional
    public void applyOperations(Long userId, String analysisId, List<WeaknessMemoryOperation> operations) {
        if (userId == null || !StringUtils.hasText(analysisId) || CollectionUtils.isEmpty(operations)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (WeaknessMemoryOperation op : operations) {
            if (op == null || !StringUtils.hasText(op.op())) {
                continue;
            }
            String kind = op.op().trim().toUpperCase();
            switch (kind) {
                case WeaknessMemoryOperation.OP_SKIP -> {
                    // no-op
                }
                case WeaknessMemoryOperation.OP_CREATE -> applyCreate(userId, analysisId, op, now);
                case WeaknessMemoryOperation.OP_MERGE -> applyMerge(userId, analysisId, op, now);
                default -> log.warn("unknown weakness memory op={} analysisId={}", kind, analysisId);
            }
        }
        capacitySupport.enforceActivePoolLimit(userId);
    }

    @Transactional
    public int deleteBySourceAnalysis(Long userId, String analysisId) {
        if (userId == null || !StringUtils.hasText(analysisId)) {
            return 0;
        }
        return memoryMapper.deleteByUserIdAndSourceAnalysisId(userId, analysisId.trim());
    }

    private void applyCreate(Long userId, String analysisId, WeaknessMemoryOperation op, LocalDateTime now) {
        if (!StringUtils.hasText(op.title())) {
            log.warn("weakness CREATE rejected: missing title analysisId={}", analysisId);
            return;
        }
        UserWeaknessMemory row = UserWeaknessMemory.builder()
                .userId(userId)
                .title(op.title().trim())
                .anchorHabitKey(trimToNull(op.anchorHabitKey()))
                .anchorPointId(trimToNull(op.anchorPointId()))
                .sourceAnalysisId(analysisId)
                .hitCount(1)
                .practicedCount(0)
                .status(UserWeaknessMemory.STATUS_ACTIVE)
                .lastSeenAt(now)
                .build();
        memoryMapper.insert(row);
    }

    private void applyMerge(Long userId, String analysisId, WeaknessMemoryOperation op, LocalDateTime now) {
        if (op.memoryId() == null) {
            log.warn("weakness MERGE rejected: missing memoryId analysisId={}", analysisId);
            return;
        }
        if (!StringUtils.hasText(op.title())) {
            log.warn("weakness MERGE rejected: missing title memoryId={} analysisId={}",
                    op.memoryId(), analysisId);
            return;
        }
        var existingOpt = memoryMapper.findByIdAndUserId(op.memoryId(), userId);
        if (existingOpt.isEmpty()) {
            log.warn("weakness MERGE rejected: memory not owned memoryId={} userId={}", op.memoryId(), userId);
            return;
        }
        UserWeaknessMemory existing = existingOpt.get();
        int hits = existing.getHitCount() == null ? 1 : existing.getHitCount();
        existing.setTitle(op.title().trim());
        existing.setAnchorHabitKey(trimToNull(op.anchorHabitKey()));
        existing.setAnchorPointId(trimToNull(op.anchorPointId()));
        existing.setSourceAnalysisId(analysisId);
        existing.setHitCount(hits + 1);
        existing.setLastSeenAt(now);
        existing.setStatus(UserWeaknessMemory.STATUS_ACTIVE);
        memoryMapper.updateMerged(existing);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
