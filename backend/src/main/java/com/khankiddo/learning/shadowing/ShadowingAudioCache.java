package com.khankiddo.learning.shadowing;

import com.khankiddo.learning.config.ShadowingProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 原声 mp3 磁盘缓存：同一句（音色 + 文本）只向 TTS 请求一次。目录按哈希前两位分片。
 */
@Slf4j
@Component
public class ShadowingAudioCache {

    private final Path root;

    @Autowired
    public ShadowingAudioCache(ShadowingProperties properties) {
        this(Path.of(properties.getTts().getCacheDir()));
    }

    ShadowingAudioCache(Path root) {
        this.root = root;
    }

    public static String key(String voice, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((voice + "\n" + text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Optional<byte[]> get(String key) {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException ex) {
            log.warn("读取跟读原声缓存失败 {}: {}", file, ex.toString());
            return Optional.empty();
        }
    }

    public void put(String key, byte[] audio) {
        Path file = fileFor(key);
        Path tmp = null;
        try {
            Files.createDirectories(file.getParent());
            tmp = Files.createTempFile(file.getParent(), key, ".part");
            Files.write(tmp, audio);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            log.warn("写入跟读原声缓存失败 {}: {}", file, ex.toString());
            if (ObjectUtils.isNotEmpty(tmp)) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响本次响应
                }
            }
        }
    }

    private Path fileFor(String key) {
        return root.resolve(key.substring(0, 2)).resolve(key + ".mp3");
    }
}
