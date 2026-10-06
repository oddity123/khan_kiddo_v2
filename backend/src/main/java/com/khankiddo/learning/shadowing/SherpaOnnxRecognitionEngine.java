package com.khankiddo.learning.shadowing;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * sherpa-onnx 非流式 Zipformer transducer。模型目录需含 encoder / decoder / joiner 的 onnx 与 tokens.txt；
 * encoder 优先 int8（体积约为 fp32 的 1/4），decoder / joiner 体积小，优先 fp32。
 */
public class SherpaOnnxRecognitionEngine implements SpeechRecognitionEngine {

    private final OfflineRecognizer recognizer;

    public SherpaOnnxRecognitionEngine(Path modelDir, int numThreads) {
        Path tokens = modelDir.resolve("tokens.txt");
        if (!Files.isRegularFile(tokens)) {
            throw new IllegalStateException("模型目录缺少 tokens.txt: " + modelDir);
        }
        OfflineTransducerModelConfig transducer = OfflineTransducerModelConfig.builder()
                .setEncoder(find(modelDir, "encoder", true))
                .setDecoder(find(modelDir, "decoder", false))
                .setJoiner(find(modelDir, "joiner", false))
                .build();
        OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                .setTransducer(transducer)
                .setTokens(tokens.toString())
                .setNumThreads(Math.max(1, numThreads))
                .setDebug(false)
                .build();
        OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build();
        this.recognizer = new OfflineRecognizer(config);
    }

    @Override
    public String transcribe(float[] samples, int sampleRate) {
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, sampleRate);
            recognizer.decode(stream);
            return recognizer.getResult(stream).getText();
        } finally {
            stream.release();
        }
    }

    @Override
    public void close() {
        recognizer.release();
    }

    static String find(Path dir, String prefix, boolean preferInt8) {
        List<Path> candidates;
        try (Stream<Path> files = Files.list(dir)) {
            candidates = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(".onnx");
                    })
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取模型目录: " + dir, ex);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("模型目录缺少 " + prefix + "*.onnx: " + dir);
        }
        return candidates.stream()
                .filter(p -> p.getFileName().toString().endsWith(".int8.onnx") == preferInt8)
                .findFirst()
                .orElse(candidates.get(0))
                .toString();
    }
}
