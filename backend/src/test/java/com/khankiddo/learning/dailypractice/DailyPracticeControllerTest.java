package com.khankiddo.learning.dailypractice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.mapper.UserWeaknessMemoryMapper;
import com.khankiddo.learning.model.UserWeaknessMemory;
import com.khankiddo.learning.weaknessmemory.WeaknessMemoryConsolidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyPracticeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserWeaknessMemoryMapper memoryMapper;

    @MockBean
    private WeaknessMemoryConsolidator consolidator;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM user_weakness_memory");
        jdbcTemplate.update("DELETE FROM growth_card_evidence");
        jdbcTemplate.update("DELETE FROM growth_card");
        jdbcTemplate.update("DELETE FROM conversation_analysis_item");
        jdbcTemplate.update("DELETE FROM conversation_analysis");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void recommendRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/practice/daily/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recommendRotatesWithExcludeAndMarkPracticed() throws Exception {
        String token = registerAndLogin("dailyuser");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "dailyuser");

        LocalDateTime seen = LocalDateTime.now().minusDays(1);
        memoryMapper.insert(UserWeaknessMemory.builder()
                .userId(userId)
                .title("高复发")
                .anchorHabitKey("FAM_NONFINITE")
                .anchorPointId("NF_GERUND_OBJECT")
                .sourceAnalysisId("a-1")
                .hitCount(8)
                .practicedCount(0)
                .status(UserWeaknessMemory.STATUS_ACTIVE)
                .lastSeenAt(seen)
                .build());
        memoryMapper.insert(UserWeaknessMemory.builder()
                .userId(userId)
                .title("次复发")
                .anchorHabitKey("FAM_ARTICLE")
                .anchorPointId("ART_DEFINITE")
                .sourceAnalysisId("a-2")
                .hitCount(3)
                .practicedCount(0)
                .status(UserWeaknessMemory.STATUS_ACTIVE)
                .lastSeenAt(seen)
                .build());

        MvcResult first = mockMvc.perform(post("/api/practice/daily/recommend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.goals[0].title").value("高复发"))
                .andExpect(jsonPath("$.prompt").isString())
                .andReturn();

        long firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("goals").get(0).path("memoryId").asLong();

        mockMvc.perform(post("/api/practice/daily/recommend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excludeMemoryIds\":[" + firstId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals[0].title").value("次复发"));

        mockMvc.perform(post("/api/practice/daily/mark-practiced")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoryIds\":[" + firstId + "]}"))
                .andExpect(status().isOk());

        UserWeaknessMemory marked = memoryMapper.findByIdAndUserId(firstId, userId).orElseThrow();
        assertThat(marked.getPracticedCount()).isEqualTo(1);
        assertThat(marked.getLastPracticedAt()).isNotNull();
    }

    @Test
    void emptyRecommendReturnsFallback() throws Exception {
        String token = registerAndLogin("emptyuser");
        mockMvc.perform(post("/api/practice/daily/recommend")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.prompt").value(DailyPracticeService.EMPTY_FALLBACK_PROMPT));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"secret12\",\"email\":\"" + username + "@example.com\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret12\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.path("token").asText();
    }
}
