package com.khankiddo.learning.dto.dailypractice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPracticeRecommendRequest {

    /** 换一组时排除已展示的薄弱点记忆 id。 */
    private List<Long> excludeMemoryIds;

    /** 换一组时排除已展示的成长卡 cardId。 */
    private List<String> excludeCardIds;
}
