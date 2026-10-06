package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.config.ShadowingProperties;
import com.khankiddo.learning.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechRecognizerHolderTest {

    private static ShadowingProperties props(boolean enabled, int maxConcurrent, int maxQueue) {
        ShadowingProperties properties = new ShadowingProperties();
        properties.setEnabled(enabled);
        properties.setModelDir("/models/fake");
        properties.setMaxConcurrent(maxConcurrent);
        properties.setMaxQueue(maxQueue);
        properties.setQueueTimeout(Duration.ofMillis(200));
        return properties;
    }

    private static SpeechRecognitionEngine fixed(String text) {
        return new SpeechRecognitionEngine() {
            @Override
            public String transcribe(float[] samples, int sampleRate) {
                return text;
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void disabledNeverLoadsEngine() {
        AtomicBoolean created = new AtomicBoolean();
        SpeechRecognizerHolder holder = new SpeechRecognizerHolder(props(false, 1, 2), () -> {
            created.set(true);
            return fixed("x");
        });
        holder.init();

        assertFalse(created.get());
        assertFalse(holder.isReady());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> holder.recognize(new float[1]));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void loadFailureIsSwallowed() {
        SpeechRecognizerHolder holder = new SpeechRecognizerHolder(props(true, 1, 2), () -> {
            throw new UnsatisfiedLinkError("no native lib");
        });
        holder.init();

        assertFalse(holder.isReady());
    }

    @Test
    void recognizesWhenReady() {
        SpeechRecognizerHolder holder = new SpeechRecognizerHolder(props(true, 1, 2), () -> fixed("hello"));
        holder.init();

        assertTrue(holder.isReady());
        assertEquals("hello", holder.recognize(new float[1]));
    }

    @Test
    void rejectsWhenRunningAndQueueAreFull() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SpeechRecognitionEngine blocking = new SpeechRecognitionEngine() {
            @Override
            public String transcribe(float[] samples, int sampleRate) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            }

            @Override
            public void close() {
            }
        };
        SpeechRecognizerHolder holder = new SpeechRecognizerHolder(props(true, 1, 0), () -> blocking);
        holder.init();

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> holder.recognize(new float[1]));
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertThrows(TooManyRequestsException.class, () -> holder.recognize(new float[1]));

        release.countDown();
        assertEquals("done", first.get(5, TimeUnit.SECONDS));
        assertEquals("done", holder.recognize(new float[1]));
    }

    @Test
    void queuedRequestTimesOut() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SpeechRecognitionEngine blocking = new SpeechRecognitionEngine() {
            @Override
            public String transcribe(float[] samples, int sampleRate) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            }

            @Override
            public void close() {
            }
        };
        SpeechRecognizerHolder holder = new SpeechRecognizerHolder(props(true, 1, 1), () -> blocking);
        holder.init();

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> holder.recognize(new float[1]));
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertThrows(TooManyRequestsException.class, () -> holder.recognize(new float[1]));

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
    }
}
