package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 卡片的静态行动提示：原句 → 目标句 + 教练提示，MVP 由字典模板 + 本场证据拼出，无 LLM。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticePromptDto {

    /** 本场第一条证据的原句 */
    private String originalSentence;

    /** 建议改说的目标句 / 目标表达 */
    private String targetSentence;

    /** 中文教练提示，取自字典 actionHintZh */
    private String coachingZh;
}
