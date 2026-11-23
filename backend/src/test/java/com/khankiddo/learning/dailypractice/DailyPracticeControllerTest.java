package com.khankiddo.learning.dailypractice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.memory.assessment.MemoryAssessmentLlmClient;
import com.khankiddo.learning.memory.domain.PracticeSession;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.enums.PracticeSessionStatus;
import com.khankiddo.learning.memory.mapper.PracticeSessionMapper;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private WeaknessMemoryMapper memoryMapper;
    @Autowired
    private PracticeSessionMapper sessionMapper;

    @MockBean
    private MemoryAssessmentLlmClient assessmentLlmClient;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM practice_attempt");
        jdbcTemplate.update("DELETE FROM practice_session");
        jdbcTemplate.update("DELETE FROM memory_evidence");
        jdbcTemplate.update("DELETE FROM weakness_memory");
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
    void recommendRotatesWithExcludeAndMarkPracticedCreatesSession() throws Exception {
        String token = registerAndLogin("dailyuser");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "dailyuser");

        LocalDateTime seen = LocalDateTime.now().minusDays(1);
        memoryMapper.insert(WeaknessMemory.builder()
                .userId(userId)
                .title("高复发")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_TENSE")
                .pointId("PAST_SIMPLE_DONE")
                .status(MemoryStatus.ACTIVE.name())
                .hitCount(8)
                .recentHitCount(6)
                .practicedCount(0)
                .correctCount(0)
                .masteryLevel(BigDecimal.ZERO)
                .consecutiveCorrect(0)
                .firstSeenAt(seen.minusDays(5))
                .lastSeenAt(seen)
                .build());
        memoryMapper.insert(WeaknessMemory.builder()
                .userId(userId)
                .title("次复发")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_ARTICLE")
                .pointId("ARTICLE_A_AN")
                .status(MemoryStatus.ACTIVE.name())
                .hitCount(2)
                .recentHitCount(2)
                .practicedCount(0)
                .correctCount(0)
                .masteryLevel(BigDecimal.ZERO)
                .consecutiveCorrect(0)
                .firstSeenAt(seen.minusDays(3))
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
                        .content("{\"memoryIds\":[" + firstId + "],\"sourceConversationId\":\"conv-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionIds[0]").isNumber());

        WeaknessMemory marked = memoryMapper.findByIdAndUserId(firstId, userId).orElseThrow();
        assertThat(marked.getStatus()).isEqualTo(MemoryStatus.PRACTICING.name());
        assertThat(marked.getPracticedCount()).isEqualTo(1);
        assertThat(marked.getLastPracticedAt()).isNotNull();

        List<PracticeSession> sessions = sessionMapper.findByMemoryId(firstId);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getStatus()).isEqualTo(PracticeSessionStatus.IN_PROGRESS.name());
        assertThat(sessions.get(0).getSourceConversationId()).isEqualTo("conv-123");
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
