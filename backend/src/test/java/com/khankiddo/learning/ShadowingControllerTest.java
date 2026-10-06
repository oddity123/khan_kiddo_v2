package com.khankiddo.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.config.ShadowingProperties;
import com.khankiddo.learning.exception.TooManyRequestsException;
import com.khankiddo.learning.shadowing.EdgeTtsClient;
import com.khankiddo.learning.shadowing.SpeechRecognizerHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShadowingControllerTest {

    private static final String BASE = "/api/shadowing/analyses/sh-a1/sentences/";

    @TempDir
    static Path cacheDir;

    @DynamicPropertySource
    static void shadowingProps(DynamicPropertyRegistry registry) {
        registry.add("app.shadowing.tts.enabled", () -> "true");
        registry.add("app.shadowing.tts.cache-dir", () -> cacheDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShadowingProperties properties;

    @MockitoBean
    private SpeechRecognizerHolder recognizer;

    @MockitoBean
    private EdgeTtsClient ttsClient;

    @BeforeEach
    void setUp() throws Exception {
        try (Stream<Path> children = Files.list(cacheDir)) {
            for (Path child : children.toList()) {
                FileSystemUtils.deleteRecursively(child);
            }
        }
        jdbcTemplate.update("DELETE FROM conversation_analysis_item");
        jdbcTemplate.update("DELETE FROM conversation_analysis");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("""
                INSERT INTO users (username, password, email, enabled, role)
                VALUES ('admin', 'hash', 'admin@test.com', 1, 'ADMIN'),
                       ('owner', 'hash', 'owner@test.com', 1, 'USER'),
                       ('other', 'hash', 'other@test.com', 1, 'USER')
                """);
        Long ownerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'owner'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO conversation_analysis (user_id, analysis_id, conversation_content, status)
                VALUES (?, 'sh-a1', 'Yesterday I go to school.', 'success')
                """, ownerId);
        jdbcTemplate.update("""
                INSERT INTO conversation_analysis_item
                    (analysis_id, sentence_id, original_sentence, point_id, error_point, suggestion)
                VALUES ('sh-a1', 1, 'Yesterday I go to school.', 'p1', 'go→went', 'Yesterday I went to school.'),
                       ('sh-a1', 2, 'Fine.', 'p2', 'none', '  ')
                """);
        properties.setAdminOnly(false);
        when(recognizer.isReady()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        properties.setAdminOnly(false);
    }

    @Test
    void status_requiresLogin() throws Exception {
        mockMvc.perform(get("/api/shadowing/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_reportsEnabledFeatures() throws Exception {
        mockMvc.perform(get("/api/shadowing/status").header("Authorization", bearer("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoringEnabled").value(true))
                .andExpect(jsonPath("$.ttsEnabled").value(true));
    }

    @Test
    void adminOnly_hidesFeatureFromNormalUsers() throws Exception {
        properties.setAdminOnly(true);

        mockMvc.perform(get("/api/shadowing/status").header("Authorization", bearer("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoringEnabled").value(false))
                .andExpect(jsonPath("$.ttsEnabled").value(false));
        mockMvc.perform(get(BASE + "1/audio").header("Authorization", bearer("owner")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/shadowing/status").header("Authorization", bearer("admin")))
                .andExpect(jsonPath("$.scoringEnabled").value(true));
    }

    @Test
    void audio_synthesizesOnceThenServesFromCache() throws Exception {
        when(ttsClient.synthesize(anyString(), anyString())).thenReturn(Optional.of(new byte[]{9, 8, 7}));
        String token = bearer("owner");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(BASE + "1/audio").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("audio/mpeg"))
                    .andExpect(content().bytes(new byte[]{9, 8, 7}));
        }
        verify(ttsClient, times(1)).synthesize("Yesterday I went to school.", "en-US-AriaNeural");
    }

    @Test
    void audio_returns503WhenTtsFails() throws Exception {
        when(ttsClient.synthesize(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "1/audio").header("Authorization", bearer("owner")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("原声暂不可用"));
    }

    @Test
    void audio_rejectsOtherUsersAnalysis() throws Exception {
        mockMvc.perform(get(BASE + "1/audio").header("Authorization", bearer("other")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("分析记录不存在"));
        verify(ttsClient, never()).synthesize(anyString(), anyString());
    }

    @Test
    void audio_rejectsSentenceWithoutSuggestion() throws Exception {
        mockMvc.perform(get(BASE + "2/audio").header("Authorization", bearer("owner")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该句没有可跟读的改写"));
    }

    @Test
    void score_returnsWordLevelResult() throws Exception {
        when(recognizer.recognize(any())).thenReturn("YESTERDAY I GO TO SCHOOL");

        mockMvc.perform(multipart(BASE + "1/score").file(wavFile()).header("Authorization", bearer("owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.recognizedText").value("YESTERDAY I GO TO SCHOOL"))
                .andExpect(jsonPath("$.words[2].text").value("went"))
                .andExpect(jsonPath("$.words[2].hit").value(false))
                .andExpect(jsonPath("$.words[2].scored").value(true));
    }

    @Test
    void score_returns503WhenRecognizerNotReady() throws Exception {
        when(recognizer.isReady()).thenReturn(false);

        mockMvc.perform(multipart(BASE + "1/score").file(wavFile()).header("Authorization", bearer("owner")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void score_returns429WhenBusy() throws Exception {
        when(recognizer.recognize(any())).thenThrow(new TooManyRequestsException("当前跟读人数较多，请稍后再试"));

        mockMvc.perform(multipart(BASE + "1/score").file(wavFile()).header("Authorization", bearer("owner")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void score_rejectsInvalidWav() throws Exception {
        MockMultipartFile garbage = new MockMultipartFile("audio", "a.wav", "audio/wav", "nope".getBytes());

        mockMvc.perform(multipart(BASE + "1/score").file(garbage).header("Authorization", bearer("owner")))
                .andExpect(status().isBadRequest());
    }

    private static MockMultipartFile wavFile() {
        short[] samples = new short[1600];
        ByteBuffer buf = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples.length * 2)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples.length * 2);
        return new MockMultipartFile("audio", "a.wav", "audio/wav", buf.array());
    }

    private String bearer(String username) throws Exception {
        jdbcTemplate.update("UPDATE users SET password = ? WHERE username = ?",
                new BCryptPasswordEncoder().encode("secret12"), username);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret12"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return "Bearer " + body.get("token").asText();
    }
}
