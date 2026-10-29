package com.khankiddo.learning.dto.voice;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VoicePracticeBeginRequest {

    private List<String> cardIds = new ArrayList<>();

    private List<String> analysisIds = new ArrayList<>();
}
