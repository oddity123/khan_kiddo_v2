package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisItemDto {

    private Long sentenceId;
    private String originalSentence;
    private String suggestion;
    private List<AnalysisErrorDto> errors;
    /**
     * ERRANT 空白分词结果（与 edits 下标对齐）；无批注时为 null。
     */
    private List<String> originalTokens;
    private List<String> correctedTokens;
    /** R/M/U 操作编辑；软依赖失败或未启用时为 null/空 */
    private List<SentenceEditDto> edits;
}
