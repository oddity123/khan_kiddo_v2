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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

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
                VALUES ('admin', 'hash', 'admin@test.com', 1, 'ADMIN'),
                       ('normal', 'hash', 'user@test.com', 1, 'USER')
                """);
    }

    @Test
    void listUsers_requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_forbiddenForNormalUser() throws Exception {
        String token = loginAndGetToken("normal");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无管理员权限"));
    }

    @Test
    void listUsers_successForAdmin() throws Exception {
        String token = loginAndGetToken("admin");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.records[0].username").exists());
    }

    @Test
    void listUsers_filterByAnalysisCount() throws Exception {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'normal'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO conversation_analysis (user_id, analysis_id, conversation_content, status,
                                                   processing_time_ms, created_at, updated_at)
                VALUES (?, 'count-test-1', 'Hello', 'success', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        String token = loginAndGetToken("admin");
        mockMvc.perform(get("/api/admin/users")
                        .param("minAnalysisCount", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[?(@.username=='normal')].analysisCount").value(1));
        mockMvc.perform(get("/api/admin/users")
                        .param("maxAnalysisCount", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[?(@.username=='normal')]").isEmpty());
    }

    @Test
    void listAnalyses_filterByUsername() throws Exception {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'normal'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO conversation_analysis (user_id, analysis_id, conversation_content, status,
                                                   processing_time_ms, created_at, updated_at)
                VALUES (?, 'test-analysis-1', 'Hello world conversation', 'success', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        String token = loginAndGetToken("admin");
        mockMvc.perform(get("/api/admin/analyses")
                        .param("username", "normal")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].username").value("normal"));
    }

    @Test
    void listAnalyses_successForAdmin() throws Exception {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'normal'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO conversation_analysis (user_id, analysis_id, conversation_content, status,
                                                   processing_time_ms, created_at, updated_at)
                VALUES (?, 'test-analysis-1', 'Hello world conversation', 'success', 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        String token = loginAndGetToken("admin");
        mockMvc.perform(get("/api/admin/analyses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].username").value("normal"))
                .andExpect(jsonPath("$.records[0].analysisId").value("test-analysis-1"));
    }

    @Test
    void listAnalyses_forbiddenForNormalUser() throws Exception {
        String token = loginAndGetToken("normal");
        mockMvc.perform(get("/api/admin/analyses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPointDictionary_successForAdmin() throws Exception {
        String token = loginAndGetToken("admin");
        mockMvc.perform(get("/api/admin/knowledge/point-dictionary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.stats.pointCount").value(39))
                .andExpect(jsonPath("$.families.length()").value(12))
                .andExpect(jsonPath("$.discriminators.length()").value(5));
    }

    @Test
    void getPointDictionary_forbiddenForNormalUser() throws Exception {
        String token = loginAndGetToken("normal");
        mockMvc.perform(get("/api/admin/knowledge/point-dictionary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String loginAndGetToken(String username) throws Exception {
        jdbcTemplate.update(
                "UPDATE users SET password = ? WHERE username = ?",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("secret12"),
                username);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret12"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
