package com.khankiddo.learning.dto.shadowing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowingScoreDto {

    /** 0–100 */
    private int score;

    private boolean passed;

    private String recognizedText;

    /** 按目标句顺序 */
    private List<ShadowingWordDto> words;
}
