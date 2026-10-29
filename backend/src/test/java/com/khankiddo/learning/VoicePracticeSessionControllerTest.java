package com.khankiddo.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khankiddo.learning.config.VoicePracticeCapProperties;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoicePracticeSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VoicePracticeCapProperties caps;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_funnel_event");
        jdbcTemplate.update("DELETE FROM voice_practice_session");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void capsBindFromProperties() {
        assertThat(caps.userDailyLimitSeconds()).isEqualTo(8 * 60);
        assertThat(caps.siteMonthlyLimitSeconds()).isEqualTo(600 * 60);
    }

    @Test
    void beginRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/voice/practice/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void beginRejectsForeignGrowthCard() throws Exception {
        String token = login("picker");
        mockMvc.perform(post("/api/voice/practice/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cardIds":["missing-card"],"analysisIds":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void beginPersistsEmptyMaterialSnapshot() throws Exception {
        String token = login("empty-mat");
        MvcResult result = mockMvc.perform(post("/api/voice/practice/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials.cardIds").isArray())
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
        String snap = jdbcTemplate.queryForObject(
                "select material_snapshot from voice_practice_session where id = ?",
                String.class,
                id);
        assertThat(snap).contains("cardIds");
    }

    @Test
    void savesCompletedCaptionsAndListsThemAfterRefresh() throws Exception {
        String token = login("speaker");
        long id = begin(token);

        mockMvc.perform(put("/api/voice/practice/sessions/" + id + "/transcript")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stoppedByCap":false,"lines":[
                                  {"role":"user","text":"I go to the park yesterday"},
                                  {"role":"assistant","text":"What did you do there?"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcriptSaved").value(true))
                .andExpect(jsonPath("$.transcript").value(
                        "User: I go to the park yesterday\nAssistant: What did you do there?"));

        mockMvc.perform(get("/api/voice/practice/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].transcript").value(org.hamcrest.Matchers.containsString("User:")));

        mockMvc.perform(get("/api/voice/practice/sessions/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcriptSaved").value(true));
    }

    @Test
    void capStillSavesCompletedCaptionsAndThenBlocksTheNextStart() throws Exception {
        String token = login("capped");
        long userId = userId("capped");
        jdbcTemplate.update("""
                INSERT INTO voice_practice_session
                    (user_id, started_at, ended_at, wall_duration_seconds, reserved_seconds,
                     stopped_by_cap, transcript, transcript_saved)
                VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0, 'User: earlier practice today', 1)
                """,                 userId, 8 * 60 - 30, 8 * 60 - 30);

        long id = begin(token);
        jdbcTemplate.update(
                "UPDATE voice_practice_session SET started_at = TIMESTAMPADD(SECOND, -120, started_at) WHERE id = ?",
                id);
        mockMvc.perform(put("/api/voice/practice/sessions/" + id + "/transcript")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stoppedByCap":true,"lines":[
                                  {"role":"user","text":"This sentence is long enough to keep"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedByCap").value(true))
                .andExpect(jsonPath("$.transcriptSaved").value(true))
                .andExpect(jsonPath("$.transcript").value("User: This sentence is long enough to keep"));

        mockMvc.perform(post("/api/voice/practice/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("本月练习额度已用完"));
    }

    @Test
    void unknownCaptionRoleDoesNotPretendTheTranscriptWasSaved() throws Exception {
        String token = login("partial");
        long id = begin(token);
        mockMvc.perform(put("/api/voice/practice/sessions/" + id + "/transcript")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[{"role":"partial","text":"half"}]}
                                """))
                .andExpect(status().isBadRequest());
        Integer saved = jdbcTemplate.queryForObject(
                "select cast(transcript_saved as int) from voice_practice_session where id = ?",
                Integer.class,
                id);
        assertThat(saved).isZero();
    }

    @Test
    void anotherUserCannotReadTheSession() throws Exception {
        String owner = login("owner");
        long id = begin(owner);
        String other = login("other");
        mockMvc.perform(get("/api/voice/practice/sessions/" + id)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guestAnalyzeStartIsCountedAndLoggedInIsNotGuest() throws Exception {
        mockMvc.perform(post("/api/funnel/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"analyze_start"}
                                """))
                .andExpect(status().isOk());
        Integer guest = jdbcTemplate.queryForObject(
                "select cast(guest as int) from product_funnel_event where event_name = 'analyze_start'",
                Integer.class);
        assertThat(guest).isEqualTo(1);

        String token = login("member");
        mockMvc.perform(post("/api/funnel/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"visit_analyze"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/funnel/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"analyze_start"}
                                """))
                .andExpect(status().isOk());
        Integer memberGuest = jdbcTemplate.queryForObject(
                """
                select cast(guest as int) from product_funnel_event
                where event_name = 'analyze_start' and user_id is not null
                """,
                Integer.class);
        assertThat(memberGuest).isZero();
    }

    @Test
    void funnelRejectsUnknownNamesAndVoiceEndWithoutDuration() throws Exception {
        mockMvc.perform(post("/api/funnel/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"page_view"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/funnel/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"voice_end"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/funnel/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"voice_saved","voiceSessionId":4}
                                """))
                .andExpect(status().isOk());
        Long sessionId = jdbcTemplate.queryForObject(
                "select voice_session_id from product_funnel_event where event_name = 'voice_saved'",
                Long.class);
        assertThat(sessionId).isEqualTo(4L);
    }

    @Test
    void registerStoresSignupFromAndDropsUnsafeMarks() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"fromuser","password":"secret12","email":"from@example.com","signupFrom":"xhs-ielts"}
                                """))
                .andExpect(status().isOk());
        String stored = jdbcTemplate.queryForObject(
                "select signup_from from users where username = 'fromuser'",
                String.class);
        assertThat(stored).isEqualTo("xhs-ielts");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"badfrom","password":"secret12","email":"bad@example.com","signupFrom":"has space"}
                                """))
                .andExpect(status().isOk());
        String dropped = jdbcTemplate.queryForObject(
                "select signup_from from users where username = 'badfrom'",
                String.class);
        assertThat(dropped).isNull();
    }

    private long begin(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/voice/practice/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedSeconds").isNumber())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("id").asLong();
    }

    private String login(String username) throws Exception {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, email, enabled, role) VALUES (?, ?, ?, 1, 'USER')",
                username,
                new BCryptPasswordEncoder().encode("secret12"),
                username + "@test.com");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret12"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private long userId(String username) {
        Long id = jdbcTemplate.queryForObject(
                "select id from users where username = ?",
                Long.class,
                username);
        assertThat(id).isNotNull();
        return id;
    }
}
