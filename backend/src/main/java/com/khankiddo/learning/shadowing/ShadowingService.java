package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.config.ShadowingProperties;
import com.khankiddo.learning.dto.shadowing.ShadowingScoreDto;
import com.khankiddo.learning.dto.shadowing.ShadowingStatusDto;
import com.khankiddo.learning.exception.BadRequestException;
import com.khankiddo.learning.exception.ForbiddenException;
import com.khankiddo.learning.mapper.ConversationAnalysisItemMapper;
import com.khankiddo.learning.mapper.ConversationAnalysisMapper;
import com.khankiddo.learning.model.ConversationAnalysisItem;
import com.khankiddo.learning.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;

/**
 * 影子跟读：只按「当前用户的分析 + 句子 id」取 AI 改写句，不接受任意文本，避免接口被当作通用 TTS / 识别服务。
 */
@Service
@RequiredArgsConstructor
public class ShadowingService {

    private final ShadowingProperties properties;
    private final SpeechRecognizerHolder recognizer;
    private final EdgeTtsClient ttsClient;
    private final ShadowingAudioCache audioCache;
    private final ConversationAnalysisMapper analysisMapper;
    private final ConversationAnalysisItemMapper itemMapper;

    public ShadowingStatusDto status() {
        SecurityUtils.requireUserId();
        boolean allowed = isAllowed();
        return ShadowingStatusDto.builder()
                .scoringEnabled(allowed && recognizer.isReady())
                .ttsEnabled(allowed && properties.getTts().isEnabled())
                .build();
    }

    public byte[] audio(String analysisId, Long sentenceId) {
        requireAccess();
        if (!properties.getTts().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "原声暂不可用");
        }
        String text = requireTargetSentence(analysisId, sentenceId);
        String voice = properties.getTts().getVoice();
        String key = ShadowingAudioCache.key(voice, text);
        Optional<byte[]> cached = audioCache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        byte[] audio = ttsClient.synthesize(text, voice)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "原声暂不可用"));
        audioCache.put(key, audio);
        return audio;
    }

    public ShadowingScoreDto score(String analysisId, Long sentenceId, byte[] wav) {
        requireAccess();
        if (!recognizer.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, SpeechRecognizerHolder.UNAVAILABLE_MESSAGE);
        }
        String target = requireTargetSentence(analysisId, sentenceId);
        float[] samples = WavPcmDecoder.decode(wav, properties.getMaxAudioSeconds());
        String recognized = recognizer.recognize(samples);
        return ShadowingWordMatcher.match(target, recognized, properties.getPassScore());
    }

    private boolean isAllowed() {
        return !properties.isAdminOnly() || SecurityUtils.isAdmin();
    }

    private void requireAccess() {
        SecurityUtils.requireUserId();
        if (!isAllowed()) {
            throw new ForbiddenException("跟读功能暂未开放");
        }
    }

    private String requireTargetSentence(String analysisId, Long sentenceId) {
        Long userId = SecurityUtils.requireUserId();
        analysisMapper.findByAnalysisIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new BadRequestException("分析记录不存在"));
        return itemMapper.findByAnalysisId(analysisId).stream()
                .filter(item -> Objects.equals(item.getSentenceId(), sentenceId))
                .map(ConversationAnalysisItem::getSuggestion)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("该句没有可跟读的改写"));
    }
}
