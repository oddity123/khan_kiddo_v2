package com.khankiddo.learning.controller;

import com.khankiddo.learning.dto.shadowing.ShadowingScoreDto;
import com.khankiddo.learning.dto.shadowing.ShadowingStatusDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.shadowing.ShadowingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/api/shadowing")
@RequiredArgsConstructor
public class ShadowingController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final ShadowingService shadowingService;

    @GetMapping("/status")
    public ShadowingStatusDto status() {
        return shadowingService.status();
    }

    @GetMapping("/analyses/{analysisId}/sentences/{sentenceId}/audio")
    public ResponseEntity<byte[]> audio(@PathVariable String analysisId, @PathVariable Long sentenceId) {
        byte[] audio = shadowingService.audio(analysisId, sentenceId);
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(audio);
    }

    @PostMapping(value = "/analyses/{analysisId}/sentences/{sentenceId}/score",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ShadowingScoreDto score(@PathVariable String analysisId,
                                   @PathVariable Long sentenceId,
                                   @RequestParam("audio") MultipartFile audio) {
        try {
            return shadowingService.score(analysisId, sentenceId, audio.getBytes());
        } catch (IOException ex) {
            throw new BadRequestException("录音上传失败，请重试");
        }
    }
}
