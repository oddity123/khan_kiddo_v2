package com.khankiddo.learning.dto.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stage 3 为 Top 行动卡生成的本场诊断文案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionCardDiagnosisDto {

    /** 对应 Top 行动卡排名，1-based；由模型原样复制输入 rank。 */
    private Integer rank;

    /** 后端稳定分组键；优先用它对齐重新计算出的行动卡。 */
    private String habitKey;

    /** 代表知识点 ID；当 habitKey 对齐失败时作为兜底。 */
    private String pointId;

    /** 一段本场专属诊断，描述具体问题模式；不写练习建议或鼓励话。 */
    private String diagnosisZh;
}
