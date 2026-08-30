package com.khankiddo.learning.dto.conversation;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ERRANT 单条编辑；{@code op} 仅为操作前缀 R/M/U（错误细类暂不使用）。
 * token 下标均为半开区间。
 * <p>
 * Lombok {@code getOStart()} 会被 Jackson 写成 {@code ostart}，须显式指定 camelCase，
 * 否则前端读不到下标、高亮全部失效。{@link JsonAlias} 兼容已落库的旧字段名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SentenceEditDto {

    /** R=替换，M=缺失（需插入），U=多余（需删除） */
    private String op;

    @Getter(onMethod_ = {@JsonProperty("oStart"), @JsonAlias({"ostart", "o_start"})})
    @Setter(onMethod_ = {@JsonProperty("oStart"), @JsonAlias({"ostart", "o_start"})})
    private int oStart;

    @Getter(onMethod_ = {@JsonProperty("oEnd"), @JsonAlias({"oend", "o_end"})})
    @Setter(onMethod_ = {@JsonProperty("oEnd"), @JsonAlias({"oend", "o_end"})})
    private int oEnd;

    @Getter(onMethod_ = {@JsonProperty("oStr"), @JsonAlias({"ostr", "o_str"})})
    @Setter(onMethod_ = {@JsonProperty("oStr"), @JsonAlias({"ostr", "o_str"})})
    private String oStr;

    @Getter(onMethod_ = {@JsonProperty("cStart"), @JsonAlias({"cstart", "c_start"})})
    @Setter(onMethod_ = {@JsonProperty("cStart"), @JsonAlias({"cstart", "c_start"})})
    private int cStart;

    @Getter(onMethod_ = {@JsonProperty("cEnd"), @JsonAlias({"cend", "c_end"})})
    @Setter(onMethod_ = {@JsonProperty("cEnd"), @JsonAlias({"cend", "c_end"})})
    private int cEnd;

    @Getter(onMethod_ = {@JsonProperty("cStr"), @JsonAlias({"cstr", "c_str"})})
    @Setter(onMethod_ = {@JsonProperty("cStr"), @JsonAlias({"cstr", "c_str"})})
    private String cStr;
}
