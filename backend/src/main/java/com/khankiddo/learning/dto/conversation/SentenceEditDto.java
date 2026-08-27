package com.khankiddo.learning.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ERRANT 单条编辑；{@code op} 仅为操作前缀 R/M/U（错误细类暂不使用）。
 * token 下标均为半开区间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentenceEditDto {

    /** R=替换，M=缺失（需插入），U=多余（需删除） */
    private String op;
    private int oStart;
    private int oEnd;
    private String oStr;
    private int cStart;
    private int cEnd;
    private String cStr;
}
