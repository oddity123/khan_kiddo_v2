package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.dto.shadowing.ShadowingScoreDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真模型冒烟：需本机原生库（scripts/setup-sherpa-onnx.sh）与模型目录。
 * {@code SHADOWING_MODEL_DIR=... ./mvn.sh -q test -Dtest=SherpaOnnxRecognitionEngineIT}
 */
@EnabledIfEnvironmentVariable(named = "SHADOWING_MODEL_DIR", matches = ".+")
class SherpaOnnxRecognitionEngineIT {

    private static final String TARGET =
            "After early nightfall the yellow lamps would light up, here and there, "
                    + "the squalid quarter of the brothels.";

    @Test
    void transcribesBundledTestWav() throws Exception {
        Path modelDir = Path.of(System.getenv("SHADOWING_MODEL_DIR"));
        Path wav = modelDir.resolve("test_wavs/1089-134686-0001.wav");
        float[] samples = WavPcmDecoder.decode(Files.readAllBytes(wav), 20);

        long loadStart = System.currentTimeMillis();
        try (SherpaOnnxRecognitionEngine engine = new SherpaOnnxRecognitionEngine(modelDir, 1)) {
            long loadMs = System.currentTimeMillis() - loadStart;
            engine.transcribe(samples, WavPcmDecoder.SAMPLE_RATE);

            long start = System.currentTimeMillis();
            String text = engine.transcribe(samples, WavPcmDecoder.SAMPLE_RATE);
            long decodeMs = System.currentTimeMillis() - start;

            ShadowingScoreDto result = ShadowingWordMatcher.match(TARGET, text, 60);
            System.out.printf("load=%dms audio=%.1fs decode=%dms rss=%s text=%s score=%d%n",
                    loadMs, samples.length / 16000.0, decodeMs, rssMb(), text, result.getScore());
            assertTrue(result.getScore() >= 85, "score=" + result.getScore() + " text=" + text);
        }
    }

    private static String rssMb() {
        try {
            Process ps = new ProcessBuilder("ps", "-o", "rss=", "-p", String.valueOf(ProcessHandle.current().pid()))
                    .start();
            String kb = new String(ps.getInputStream().readAllBytes()).trim();
            return (Long.parseLong(kb) / 1024) + "MB";
        } catch (Exception ex) {
            return "?";
        }
    }
}
