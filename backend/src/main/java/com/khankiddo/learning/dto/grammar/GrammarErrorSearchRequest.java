package com.khankiddo.learning.dto.grammar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GrammarErrorSearchRequest {

    @NotBlank(message = "请输入搜索内容")
    @Size(max = 500, message = "搜索内容过长")
    private String query;

    private List<String> problemTypes;

    private Integer limit;
}
