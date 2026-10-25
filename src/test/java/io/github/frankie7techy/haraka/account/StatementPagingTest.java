package io.github.frankie7techy.haraka.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frankie7techy.haraka.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class StatementPagingTest extends IntegrationTestBase {

    private static final int DEPOSITS = 25;

    @Test
    void pagesTheStatementNewestFirstWithStableTotals() throws Exception {
        String token = registerUser(uniqueEmail());
        for (int i = 0; i < DEPOSITS; i++) {
            mockMvc.perform(post("/api/v1/accounts/me/topups")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", uniqueIdempotencyKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amountMinor\":%d}".formatted(100 + i)))
                    .andExpect(status().isCreated());
        }

        JsonNode page0 = statement(token, 0, 10);
        assertThat(page0.get("totalElements").asLong()).isEqualTo(DEPOSITS);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page0.get("entries").size()).isEqualTo(10);
        // newest first: the last deposit made (124 minor) tops the page
        assertThat(page0.get("entries").get(0).get("amountMinor").asLong()).isEqualTo(124L);

        JsonNode page2 = statement(token, 2, 10);
        assertThat(page2.get("entries").size()).isEqualTo(5);

        // ids strictly descending across a full walk
        long previous = Long.MAX_VALUE;
        for (int page = 0; page < 3; page++) {
            JsonNode current = statement(token, page, 10);
            for (JsonNode entry : current.get("entries")) {
                long id = entry.get("id").asLong();
                assertThat(id).isLessThan(previous);
                previous = id;
            }
        }
    }

    @Test
    void clampsUnreasonablePagingRequests() throws Exception {
        String token = registerUser(uniqueEmail());
        mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":500}"))
                .andExpect(status().isCreated());

        // absurd page size is clamped, negative page falls back to 0
        JsonNode hugePage = statement(token, 0, 10_000);
        assertThat(hugePage.get("size").asInt()).isLessThanOrEqualTo(100);

        JsonNode negativePage = statement(token, -5, 10);
        assertThat(negativePage.get("page").asInt()).isEqualTo(0);
        assertThat(negativePage.get("totalElements").asLong()).isEqualTo(1);
    }

    private JsonNode statement(String token, int page, int size) throws Exception {
        String body = mockMvc.perform(get("/api/v1/accounts/me/statement")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
