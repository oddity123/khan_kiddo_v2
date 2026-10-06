package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.exception.BadRequestException;
import org.apache.commons.lang3.ObjectUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 解析前端上传的 WAV：只接受 16kHz、单声道、PCM16（前端已重采样），输出 [-1, 1) 的 float 采样。
 */
public final class WavPcmDecoder {

    public static final int SAMPLE_RATE = 16000;

    private static final int HEADER_MIN_BYTES = 12;

    private WavPcmDecoder() {
    }

    public static float[] decode(byte[] wav, int maxSeconds) {
        if (ObjectUtils.isEmpty(wav) || wav.length < HEADER_MIN_BYTES
                || !"RIFF".equals(ascii(wav, 0)) || !"WAVE".equals(ascii(wav, 8))) {
            throw new BadRequestException("录音格式无效，请重新录制");
        }
        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        boolean fmtSeen = false;
        int pos = HEADER_MIN_BYTES;
        while (pos + 8 <= wav.length) {
            String id = ascii(wav, pos);
            long size = Integer.toUnsignedLong(buf.getInt(pos + 4));
            int body = pos + 8;
            if ("fmt ".equals(id)) {
                checkFormat(buf, body, size);
                fmtSeen = true;
            } else if ("data".equals(id)) {
                if (!fmtSeen) {
                    break;
                }
                int available = (int) Math.min(size, wav.length - body);
                return toSamples(buf, body, available / 2, maxSeconds);
            }
            long next = body + size + (size & 1);
            if (next > wav.length) {
                break;
            }
            pos = (int) next;
        }
        throw new BadRequestException("录音格式无效，请重新录制");
    }

    private static void checkFormat(ByteBuffer buf, int body, long size) {
        if (size < 16 || body + 16 > buf.capacity()) {
            throw new BadRequestException("录音格式无效，请重新录制");
        }
        int audioFormat = Short.toUnsignedInt(buf.getShort(body));
        int channels = Short.toUnsignedInt(buf.getShort(body + 2));
        int sampleRate = buf.getInt(body + 4);
        int bits = Short.toUnsignedInt(buf.getShort(body + 14));
        if (audioFormat != 1 || channels != 1 || sampleRate != SAMPLE_RATE || bits != 16) {
            throw new BadRequestException("录音需为 16kHz 单声道 16 位 PCM");
        }
    }

    private static float[] toSamples(ByteBuffer buf, int offset, int count, int maxSeconds) {
        if (count <= 0) {
            throw new BadRequestException("没有录到声音，请重新录制");
        }
        if (count > (long) SAMPLE_RATE * maxSeconds) {
            throw new BadRequestException("录音过长，请控制在 " + maxSeconds + " 秒内");
        }
        float[] samples = new float[count];
        for (int i = 0; i < count; i++) {
            samples[i] = buf.getShort(offset + i * 2) / 32768f;
        }
        return samples;
    }

    private static String ascii(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) {
            return "";
        }
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
