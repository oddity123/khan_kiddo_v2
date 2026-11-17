package com.khankiddo.learning.extensionconnect;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExtensionConnectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_funnel_event");
        jdbcTemplate.update("DELETE FROM extension_credential");
        jdbcTemplate.update("DELETE FROM extension_connect_session");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void configIsPublic() throws Exception {
        mockMvc.perform(get("/api/extension/connect/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.pollIntervalMs").isNumber());
    }

    @Test
    void fullConnectFlow_exchangeMeRevoke() throws Exception {
        String siteToken = registerAndLogin("extuser");
        String verifier = ExtensionConnectTokenSupport.newCodeVerifier();
        String challenge = ExtensionConnectTokenSupport.pkceS256Challenge(verifier);

        MvcResult created = mockMvc.perform(post("/api/extension/connect/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeChallenge\":\"" + challenge + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("sessionId").asText();

        mockMvc.perform(post("/api/extension/connect/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"codeVerifier\":\"" + verifier + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post("/api/extension/connect/sessions/" + sessionId + "/authorize")
                        .header("Authorization", "Bearer " + siteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"));

        MvcResult exchanged = mockMvc.perform(post("/api/extension/connect/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"codeVerifier\":\"" + verifier + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCHANGED"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.username").value("extuser"))
                .andReturn();
        String extToken = objectMapper.readTree(exchanged.getResponse().getContentAsString())
                .path("token").asText();
        assertThat(extToken).startsWith("kk_ext_");

        mockMvc.perform(post("/api/extension/connect/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"codeVerifier\":\"" + verifier + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/extension/connect/me")
                        .header("Authorization", "Bearer " + extToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.username").value("extuser"));

        mockMvc.perform(post("/api/extension/connect/revoke")
                        .header("Authorization", "Bearer " + extToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/extension/connect/me")
                        .header("Authorization", "Bearer " + extToken))
                .andExpect(status().isUnauthorized());

        Integer started = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_funnel_event WHERE event_name = 'ext_connect_started'",
                Integer.class);
        Integer authorized = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_funnel_event WHERE event_name = 'ext_connect_authorized'",
                Integer.class);
        Integer exchangedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_funnel_event WHERE event_name = 'ext_connect_exchanged'",
                Integer.class);
        Integer revoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_funnel_event WHERE event_name = 'ext_connect_revoked'",
                Integer.class);
        assertThat(started).isEqualTo(1);
        assertThat(authorized).isEqualTo(1);
        assertThat(exchangedCount).isEqualTo(1);
        assertThat(revoked).isEqualTo(1);
    }

    @Test
    void pkceMismatchFails() throws Exception {
        String siteToken = registerAndLogin("pkceuser");
        String verifier = ExtensionConnectTokenSupport.newCodeVerifier();
        String challenge = ExtensionConnectTokenSupport.pkceS256Challenge(verifier);
        String sessionId = createSession(challenge);

        mockMvc.perform(post("/api/extension/connect/sessions/" + sessionId + "/authorize")
                        .header("Authorization", "Bearer " + siteToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/extension/connect/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\",\"codeVerifier\":\""
                                + ExtensionConnectTokenSupport.newCodeVerifier() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizeRequiresLogin() throws Exception {
        String verifier = ExtensionConnectTokenSupport.newCodeVerifier();
        String sessionId = createSession(ExtensionConnectTokenSupport.pkceS256Challenge(verifier));
        mockMvc.perform(post("/api/extension/connect/sessions/" + sessionId + "/authorize"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherUserCannotAuthorizeAlreadyBoundSession() throws Exception {
        String alice = registerAndLogin("alice_ext");
        String bob = registerAndLogin("bob_ext");
        String verifier = ExtensionConnectTokenSupport.newCodeVerifier();
        String sessionId = createSession(ExtensionConnectTokenSupport.pkceS256Challenge(verifier));

        mockMvc.perform(post("/api/extension/connect/sessions/" + sessionId + "/authorize")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/extension/connect/sessions/" + sessionId + "/authorize")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isForbidden());
    }

    private String createSession(String challenge) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/extension/connect/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codeChallenge\":\"" + challenge + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("sessionId").asText();
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

