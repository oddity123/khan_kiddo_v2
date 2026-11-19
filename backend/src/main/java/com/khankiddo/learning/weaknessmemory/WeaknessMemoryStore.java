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
 * 薄弱点记忆事务落库：校验原句真实性与 memoryId 归属后执行 CREATE/MERGE，并做活跃池淘汰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeaknessMemoryStore {

    private final UserWeaknessMemoryMapper memoryMapper;
    private final WeaknessMemoryExampleSupport exampleSupport;
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
        if (!isCompletePayload(op) || !exampleSupport.isAuthenticBadExample(userId, op.badExample())) {
            log.warn("weakness CREATE rejected: incomplete or forged example analysisId={}", analysisId);
            return;
        }
        UserWeaknessMemory row = UserWeaknessMemory.builder()
                .userId(userId)
                .title(op.title().trim())
                .diagnosis(op.diagnosis().trim())
                .coaching(op.coaching().trim())
                .anchorHabitKey(trimToNull(op.anchorHabitKey()))
                .anchorPointId(trimToNull(op.anchorPointId()))
                .badExample(op.badExample().trim())
                .targetRewrite(op.targetRewrite().trim())
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
        if (!isCompletePayload(op) || !exampleSupport.isAuthenticBadExample(userId, op.badExample())) {
            log.warn("weakness MERGE rejected: incomplete or forged example memoryId={} analysisId={}",
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
        existing.setDiagnosis(op.diagnosis().trim());
        existing.setCoaching(op.coaching().trim());
        existing.setAnchorHabitKey(trimToNull(op.anchorHabitKey()));
        existing.setAnchorPointId(trimToNull(op.anchorPointId()));
        existing.setBadExample(op.badExample().trim());
        existing.setTargetRewrite(op.targetRewrite().trim());
        existing.setSourceAnalysisId(analysisId);
        existing.setHitCount(hits + 1);
        existing.setLastSeenAt(now);
        existing.setStatus(UserWeaknessMemory.STATUS_ACTIVE);
        memoryMapper.updateMerged(existing);
    }

    private static boolean isCompletePayload(WeaknessMemoryOperation op) {
        return StringUtils.hasText(op.title())
                && StringUtils.hasText(op.diagnosis())
                && StringUtils.hasText(op.coaching())
                && StringUtils.hasText(op.badExample())
                && StringUtils.hasText(op.targetRewrite());
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
