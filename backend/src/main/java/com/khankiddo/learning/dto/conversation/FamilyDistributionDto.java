package com.khankiddo.learning.dto.conversation;

import com.khankiddo.learning.knowledge.PointChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 侧栏家族分布（饼图用）：按 familyId 计数，含 cardPolicy=rare 的叶子。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyDistributionDto {

    private String familyId;
    private String titleZh;
    private PointChannel channel;
    private int count;
}
