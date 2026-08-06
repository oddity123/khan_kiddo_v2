package com.khankiddo.learning.dto.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Stage 3 的结构化返回：一组与输入 Top 行动卡对应的本场诊断。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionCardDiagnosisResultDto {

    /**
     * 与输入 Top 行动卡一一对应的诊断列表。
     * <p>
     * 每一项只描述本场问题模式，不承载练习建议；练习与复习由成长卡系统负责。
     */
    private List<ActionCardDiagnosisDto> cards;
}
