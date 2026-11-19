package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.config.WeaknessMemoryProperties;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.UserWeaknessMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 活跃池容量：新增后若超过上限，将 hit_count 最低且 last_seen_at 最早的 ACTIVE 项归档。
 */
@Component
@RequiredArgsConstructor
public class WeaknessMemoryCapacitySupport {

    private final UserWeaknessMemoryMapper memoryMapper;
    private final WeaknessMemoryProperties properties;

    public void enforceActivePoolLimit(Long userId) {
        if (userId == null) {
            return;
        }
        int limit = Math.max(1, properties.getActivePoolLimit());
        long activeCount = memoryMapper.countActiveByUserId(userId);
        if (activeCount <= limit) {
            return;
        }
        int overflow = (int) (activeCount - limit);
        List<UserWeaknessMemory> victims = memoryMapper.findActiveForEviction(userId, overflow);
        if (CollectionUtils.isEmpty(victims)) {
            return;
        }
        for (UserWeaknessMemory victim : victims) {
            memoryMapper.archiveById(victim.getId(), userId);
        }
    }
}
