package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WavPcmDecoderTest {

    static byte[] wav(int sampleRate, int channels, int bits, short[] samples, boolean extraChunk) {
        int dataBytes = samples.length * 2;
        int extra = extraChunk ? 8 + 3 + 1 : 0;
        ByteBuffer buf = ByteBuffer.allocate(44 + extra + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + extra + dataBytes);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * bits / 8);
        buf.putShort((short) (channels * bits / 8));
        buf.putShort((short) bits);
        if (extraChunk) {
            buf.put("LIST".getBytes(StandardCharsets.US_ASCII));
            buf.putInt(3);
            buf.put(new byte[]{1, 2, 3, 0});
        }
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(dataBytes);
        for (short s : samples) {
            buf.putShort(s);
        }
        return buf.array();
    }

    @Test
    void decodesMonoPcm16() {
        byte[] bytes = wav(16000, 1, 16, new short[]{0, 16384, -32768, 32767}, false);

        float[] samples = WavPcmDecoder.decode(bytes, 20);

        assertArrayEquals(new float[]{0f, 0.5f, -1f, 32767f / 32768f}, samples, 1e-6f);
    }

    @Test
    void skipsUnknownChunksWithPadding() {
        byte[] bytes = wav(16000, 1, 16, new short[]{100, 200}, true);

        assertEquals(2, WavPcmDecoder.decode(bytes, 20).length);
    }

    @Test
    void rejectsWrongSampleRate() {
        byte[] bytes = wav(44100, 1, 16, new short[]{1, 2}, false);

        assertThrows(BadRequestException.class, () -> WavPcmDecoder.decode(bytes, 20));
    }

    @Test
    void rejectsStereo() {
        byte[] bytes = wav(16000, 2, 16, new short[]{1, 2}, false);

        assertThrows(BadRequestException.class, () -> WavPcmDecoder.decode(bytes, 20));
    }

    @Test
    void rejectsTooLongAudio() {
        byte[] bytes = wav(16000, 1, 16, new short[16000 * 2 + 1], false);

        assertThrows(BadRequestException.class, () -> WavPcmDecoder.decode(bytes, 2));
    }

    @Test
    void rejectsEmptyOrGarbage() {
        assertThrows(BadRequestException.class, () -> WavPcmDecoder.decode(new byte[0], 20));
        assertThrows(BadRequestException.class, () -> WavPcmDecoder.decode("not a wav file at all, nope".getBytes(), 20));
        assertThrows(BadRequestException.class, () -> WavPcmDecoder.decode(wav(16000, 1, 16, new short[0], false), 20));
    }

    @Test
    void truncatedDataUsesAvailableBytes() {
        byte[] full = wav(16000, 1, 16, new short[]{1, 2, 3, 4}, false);
        byte[] truncated = java.util.Arrays.copyOf(full, full.length - 3);

        assertEquals(2, WavPcmDecoder.decode(truncated, 20).length);
    }
}
