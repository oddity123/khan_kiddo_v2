package com.khankiddo.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.voice.realtime.api-key=",
        "app.voice.realtime.app-id=",
        "app.voice.realtime.access-key="
})
class VoiceRealtimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM conversation_analysis");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("""
                INSERT INTO users (username, password, email, enabled, role)
                VALUES ('admin', 'hash', 'admin@test.com', 1, 'ADMIN')
                """);
    }

    @Test
    void config_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/voice/realtime/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void config_whenKeyMissing_returnsFriendlyMessage() throws Exception {
        String token = loginAndGetToken("admin");
        mockMvc.perform(get("/api/voice/realtime/config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.wsPath").value("/api/voice/realtime/ws"))
                .andExpect(jsonPath("$.model").value("1.2.6.1"))
                .andExpect(jsonPath("$.message").value(containsString("DOUBAO_SPEECH_API_KEY")));
    }

    private String loginAndGetToken(String username) throws Exception {
        jdbcTemplate.update(
                "UPDATE users SET password = ? WHERE username = ?",
                new BCryptPasswordEncoder().encode("secret12"),
                username);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret12"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
