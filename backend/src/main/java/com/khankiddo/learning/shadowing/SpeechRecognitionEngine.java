package com.khankiddo.learning.shadowing;

/**
 * 离线语音识别引擎；实现需线程安全，或由调用方限制并发。
 */
public interface SpeechRecognitionEngine extends AutoCloseable {

    String transcribe(float[] samples, int sampleRate);

    @Override
    void close();
}
