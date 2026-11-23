package com.khankiddo.learning.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.memory.domain.WeaknessMemory;
import com.khankiddo.learning.memory.enums.MemoryStatus;
import com.khankiddo.learning.memory.enums.MemoryType;
import com.khankiddo.learning.memory.mapper.WeaknessMemoryMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WeaknessMemoryMapper memoryMapper;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM practice_attempt");
        jdbcTemplate.update("DELETE FROM practice_session");
        jdbcTemplate.update("DELETE FROM memory_evidence");
        jdbcTemplate.update("DELETE FROM weakness_memory");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void fullMemoryAndPracticeSessionRestLifecycleAndUserIsolation() throws Exception {
        String aliceToken = registerAndLogin("alice_mem");
        String bobToken = registerAndLogin("bob_mem");
        Long aliceId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, "alice_mem");

        WeaknessMemory memory = WeaknessMemory.builder()
                .userId(aliceId)
                .title("一般过去时")
                .memoryType(MemoryType.GRAMMAR.name())
                .familyId("FAM_TENSE")
                .pointId("PAST_SIMPLE_DONE")
                .status(MemoryStatus.ACTIVE.name())
                .hitCount(3)
                .recentHitCount(3)
                .practicedCount(0)
                .correctCount(0)
                .masteryLevel(BigDecimal.ZERO)
                .consecutiveCorrect(0)
                .firstSeenAt(LocalDateTime.now().minusDays(4))
                .lastSeenAt(LocalDateTime.now().minusDays(1))
                .build();
        memoryMapper.insert(memory);

        // 1. Alice lists & views detail
        mockMvc.perform(get("/api/memories")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pointId").value("PAST_SIMPLE_DONE"))
                .andExpect(jsonPath("$.countsByStatus.ACTIVE").value(1));

        mockMvc.perform(get("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memory.id").value(memory.getId()));

        // 2. Bob cannot read, update, or delete Alice's memory
        mockMvc.perform(get("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/memories/" + memory.getId() + "/status")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest());

        // 3. Alice starts practice session, binds conversation, and records attempt
        MvcResult startRes = mockMvc.perform(post("/api/practice/sessions")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoryIds\":[" + memory.getId() + "],\"practiceType\":\"SCENARIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andReturn();
        long sessionId = objectMapper.readTree(startRes.getResponse().getContentAsString())
                .get(0).path("id").asLong();

        mockMvc.perform(post("/api/practice/sessions/bind-conversation")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionIds\":[" + sessionId + "],\"sourceConversationId\":\"conv-bind-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boundCount").value(1));

        mockMvc.perform(post("/api/practice/sessions/" + sessionId + "/attempts")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userResponse": "Yesterday I went home early.",
                                  "expectedResponse": "Yesterday I went home early.",
                                  "isCorrect": true,
                                  "independent": true,
                                  "hintUsed": false,
                                  "score": 95,
                                  "feedback": "准确使用了过去式 went"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(1))
                .andExpect(jsonPath("$.correctAttempts").value(1))
                .andExpect(jsonPath("$.attempts[0].attemptNo").value(1));

        mockMvc.perform(get("/api/practice/sessions?memoryId=" + memory.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceConversationId").value("conv-bind-1"));

        // 4. Alice updates status to ARCHIVED and deletes
        mockMvc.perform(patch("/api/memories/" + memory.getId() + "/status")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(delete("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        assertThat(memoryMapper.findByIdAndUserId(memory.getId(), aliceId)).isEmpty();
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
