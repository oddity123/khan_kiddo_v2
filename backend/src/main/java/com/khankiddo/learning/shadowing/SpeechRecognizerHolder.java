package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.config.ShadowingProperties;
import com.khankiddo.learning.exception.TooManyRequestsException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 进程内单例识别器：启动时加载一次模型，限制同时识别数与排队数，避免拖慢 2 核小机器上的其它接口。
 * 模型或原生库加载失败只记日志，打分接口返回 503。
 */
@Slf4j
@Component
public class SpeechRecognizerHolder {

    static final String BUSY_MESSAGE = "当前跟读人数较多，请稍后再试";
    static final String UNAVAILABLE_MESSAGE = "跟读打分暂不可用";

    private final ShadowingProperties properties;
    private final Supplier<SpeechRecognitionEngine> engineFactory;
    private final Semaphore permits;
    private final AtomicInteger inFlight = new AtomicInteger();

    private volatile SpeechRecognitionEngine engine;

    @Autowired
    public SpeechRecognizerHolder(ShadowingProperties properties) {
        this(properties, () -> new SherpaOnnxRecognitionEngine(
                Path.of(properties.getModelDir()), properties.getNumThreads()));
    }

    SpeechRecognizerHolder(ShadowingProperties properties, Supplier<SpeechRecognitionEngine> engineFactory) {
        this.properties = properties;
        this.engineFactory = engineFactory;
        this.permits = new Semaphore(Math.max(1, properties.getMaxConcurrent()), true);
    }

    @PostConstruct
    void init() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.getModelDir())) {
            log.warn("影子跟读已开启但未配置 SHADOWING_MODEL_DIR，打分不可用");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            engine = engineFactory.get();
            log.info("影子跟读识别模型已加载 dir={} 耗时={}ms", properties.getModelDir(),
                    System.currentTimeMillis() - start);
        } catch (Throwable ex) {
            log.error("影子跟读识别模型加载失败（打分不可用） dir={}: {}", properties.getModelDir(), ex.toString());
        }
    }

    public boolean isReady() {
        return ObjectUtils.isNotEmpty(engine);
    }

    public String recognize(float[] samples) {
        SpeechRecognitionEngine current = engine;
        if (ObjectUtils.isEmpty(current)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        int capacity = Math.max(1, properties.getMaxConcurrent()) + Math.max(0, properties.getMaxQueue());
        if (inFlight.incrementAndGet() > capacity) {
            inFlight.decrementAndGet();
            throw new TooManyRequestsException(BUSY_MESSAGE);
        }
        try {
            if (!permits.tryAcquire(properties.getQueueTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TooManyRequestsException(BUSY_MESSAGE);
            }
            try {
                return current.transcribe(samples, WavPcmDecoder.SAMPLE_RATE);
            } finally {
                permits.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TooManyRequestsException(BUSY_MESSAGE);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    @PreDestroy
    void close() {
        SpeechRecognitionEngine current = engine;
        engine = null;
        if (ObjectUtils.isNotEmpty(current)) {
            current.close();
        }
    }
}
