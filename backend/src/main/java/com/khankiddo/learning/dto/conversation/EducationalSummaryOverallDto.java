package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话整体文字总结（由教育总结阶段 LLM 生成）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationalSummaryOverallDto {

    /** 整体总结正文，客观概括错误类型与模式（通常 200 字以内） */
    private String levelSummary;
}
