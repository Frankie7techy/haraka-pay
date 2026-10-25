package io.github.frankie7techy.haraka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared base for API-level tests. Every test runs against the real HTTP layer, the real
 * security filter chain, the real Flyway migrations and an H2 database in PostgreSQL mode.
 * Tests use unique emails so they stay independent while sharing one cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@haraka.test";
    }

    protected String uniqueIdempotencyKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Registers a fresh user + wallet and returns the bearer token. */
    protected String registerUser(String email) throws Exception {
        MvcResult result = performRegister(email)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));
        return body.get("accessToken").asText();
    }

    protected ResultActions performRegister(String email) throws Exception {
        String body = """
                {"email":"%s","fullName":"Test User","password":"Sup3rSecret!x"}
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Registers and returns [token, accountNumber]. */
    protected String[] registerUserWithAccount(String email) throws Exception {
        String token = registerUser(email);
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("accountNumber").asText()).startsWith("HAR-");
        return new String[]{token, body.get("accountNumber").asText()};
    }

    protected String json(Object raw) {
        return raw instanceof String s ? s : raw.toString();
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
