package com.khankiddo.learning.shadowing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowingAudioCacheTest {

    @TempDir
    Path dir;

    @Test
    void keyDependsOnVoiceAndText() {
        String key = ShadowingAudioCache.key("en-US-AriaNeural", "Hello");

        assertEquals(64, key.length());
        assertEquals(key, ShadowingAudioCache.key("en-US-AriaNeural", "Hello"));
        assertNotEquals(key, ShadowingAudioCache.key("en-US-GuyNeural", "Hello"));
        assertNotEquals(key, ShadowingAudioCache.key("en-US-AriaNeural", "Hello!"));
    }

    @Test
    void putThenGetRoundTrips() throws Exception {
        ShadowingAudioCache cache = new ShadowingAudioCache(dir.resolve("tts"));
        String key = ShadowingAudioCache.key("v", "t");

        assertTrue(cache.get(key).isEmpty());
        cache.put(key, new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, cache.get(key).orElseThrow());
        try (Stream<Path> files = Files.walk(dir)) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".part")));
        }
    }
}
