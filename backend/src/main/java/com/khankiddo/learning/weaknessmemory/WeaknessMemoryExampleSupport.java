package com.khankiddo.learning.weaknessmemory;

import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 薄弱点记忆例句真实性校验：bad_example 必须能在当前用户历史错句中匹配到（忽略首尾空白）。
 */
@Component
@RequiredArgsConstructor
public class WeaknessMemoryExampleSupport {

    private final UserWeaknessMemoryMapper memoryMapper;

    public boolean isAuthenticBadExample(Long userId, String badExample) {
        if (userId == null || !StringUtils.hasText(badExample)) {
            return false;
        }
        return memoryMapper.existsOriginalSentenceByUserId(userId, badExample.trim());
    }
}
