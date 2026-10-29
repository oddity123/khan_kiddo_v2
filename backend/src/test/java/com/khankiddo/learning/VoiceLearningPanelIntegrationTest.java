package com.khankiddo.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.voice.VoiceRealtimeToolGateway;
import com.khankiddo.learning.voice.tool.AppendPracticeNoteToolHandler;
import com.khankiddo.learning.voice.tool.FocusPracticeCardToolHandler;
import com.khankiddo.learning.voice.tool.RecordPracticeAttemptToolHandler;
import com.khankiddo.learning.voice.tool.VoiceRealtimeToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 3 Done-when：工具落库后，REST hydrate 能还原焦点卡与本场证据（刷新后仍在）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoiceLearningPanelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private VoiceRealtimeToolGateway gateway;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM voice_practice_event");
        jdbc.update("DELETE FROM voice_practice_session");
        jdbc.update("DELETE FROM growth_card");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void toolsPersist_andLearningEndpointHydratesAfterReload() throws Exception {
        String token = login("learner");
        long userId = userId("learner");
        insertCard(userId, "c-in", "habit", "unfamiliar", "I go yesterday", "I went yesterday");
        insertCard(userId, "c-out", "habit", "unfamiliar", "other", "other");

        long sessionId = begin(token, "{\"cardIds\":[\"c-in\"],\"analysisIds\":[]}");
        VoiceRealtimeToolContext ctx = new VoiceRealtimeToolContext(userId, sessionId);

        var focus = gateway.execute(ctx, FocusPracticeCardToolHandler.NAME, "{\"cardId\":\"c-in\"}");
        assertThat(focus.learningEvent()).isNotNull();
        var attempt = gateway.execute(ctx, RecordPracticeAttemptToolHandler.NAME,
                "{\"cardId\":\"c-in\",\"outcome\":\"independent_success\",\"userUtterance\":\"I went there yesterday\"}");
        assertThat(attempt.outputJson()).contains("\"newStatus\":\"fuzzy\"");
        var note = gateway.execute(ctx, AppendPracticeNoteToolHandler.NAME,
                "{\"text\":\"Past tense is steady now\",\"cardId\":\"c-in\"}");
        assertThat(note.learningEvent()).isNotNull();

        var outOfScope = gateway.execute(ctx, FocusPracticeCardToolHandler.NAME, "{\"cardId\":\"c-out\"}");
        assertThat(outOfScope.outputJson()).contains("card_not_in_session");

        String status = jdbc.queryForObject(
                "select status from growth_card where card_id = 'c-in'", String.class);
        assertThat(status).isEqualTo("fuzzy");

        MvcResult result = mockMvc.perform(get("/api/voice/practice/sessions/" + sessionId + "/learning")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("focus").path("cardId").asText()).isEqualTo("c-in");
        assertThat(body.path("focus").path("front").asText()).isEqualTo("I go yesterday");
        assertThat(body.path("focus").path("status").asText()).isEqualTo("fuzzy");
        assertThat(body.path("attempts")).hasSize(1);
        assertThat(body.path("attempts").path(0).path("outcome").asText()).isEqualTo("independent_success");
        assertThat(body.path("attempts").path(0).path("statusAfter").asText()).isEqualTo("fuzzy");
        assertThat(body.path("notes")).hasSize(1);
        assertThat(body.path("notes").path(0).path("text").asText()).isEqualTo("Past tense is steady now");
    }

    @Test
    void learningEndpointRejectsOtherUsersAndAnonymous() throws Exception {
        String owner = login("owner2");
        long sessionId = begin(owner, "{}");
        String other = login("other2");

        mockMvc.perform(get("/api/voice/practice/sessions/" + sessionId + "/learning")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/voice/practice/sessions/" + sessionId + "/learning"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/voice/practice/sessions/" + sessionId + "/learning")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    private void insertCard(long userId, String cardId, String type, String status, String front, String back) {
        jdbc.update("""
                INSERT INTO growth_card (card_id, user_id, type, status, next_due_at, front, back, source_ref)
                VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?, ?)
                """, cardId, userId, type, status, front, back, "ref-" + cardId);
    }

    private long begin(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/voice/practice/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private String login(String username) throws Exception {
        jdbc.update(
                "INSERT INTO users (username, password, email, enabled, role) VALUES (?, ?, ?, 1, 'USER')",
                username, new BCryptPasswordEncoder().encode("secret12"), username + "@test.com");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secret12\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private long userId(String username) {
        Long id = jdbc.queryForObject("select id from users where username = ?", Long.class, username);
        assertThat(id).isNotNull();
        return id;
    }
}
