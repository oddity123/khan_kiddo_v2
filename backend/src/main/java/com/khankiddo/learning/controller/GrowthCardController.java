package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.growth.CollectGrowthCardRequest;
import com.khankiddo.learning.dto.growth.GrowthCardDto;
import com.khankiddo.learning.dto.growth.GrowthCardGradeRequest;
import com.khankiddo.learning.dto.growth.MintHabitGrowthCardRequest;
import com.khankiddo.learning.growth.GrowthCardReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/growth-cards")
@RequiredArgsConstructor
public class GrowthCardController {

    private final GrowthCardReviewService reviewService;

    @GetMapping("/today")
    public List<GrowthCardDto> today() {
        return reviewService.listToday();
    }

    @GetMapping("/random")
    public List<GrowthCardDto> random(@RequestParam(defaultValue = "5") int limit) {
        return reviewService.listRandom(limit);
    }

    @PostMapping("/{cardId}/grade")
    public GrowthCardDto grade(@PathVariable String cardId,
                               @Valid @RequestBody GrowthCardGradeRequest request) {
        return reviewService.grade(cardId, request);
    }

    @DeleteMapping("/{cardId}")
    public void delete(@PathVariable String cardId) {
        reviewService.delete(cardId);
    }

    @PostMapping("/collect")
    public GrowthCardDto collect(@Valid @RequestBody CollectGrowthCardRequest request) {
        return reviewService.collect(request);
    }

    @PostMapping("/mint/{analysisId}")
    public void retryMint(@PathVariable String analysisId) {
        reviewService.retryMint(analysisId);
    }

    /** 对本场指定习惯（Top2/3 等）走 LLM 生成并落库 */
    @PostMapping("/mint/{analysisId}/habit")
    public GrowthCardDto mintHabit(@PathVariable String analysisId,
                                   @Valid @RequestBody MintHabitGrowthCardRequest request) {
        return reviewService.mintHabit(analysisId, request.getHabitKey());
    }
}
