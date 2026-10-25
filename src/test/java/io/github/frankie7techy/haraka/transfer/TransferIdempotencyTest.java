package io.github.frankie7techy.haraka.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.frankie7techy.haraka.IntegrationTestBase;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class TransferIdempotencyTest extends IntegrationTestBase {

    private String topup(String token, long amountMinor) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":%d}".formatted(amountMinor)))
                .andExpect(status().isCreated());
        return token;
    }

    private long statementTotal(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/me/statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(objectMapper.readTree(result.getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("totalElements").asText());
    }

    private String transfer(String token, String target, long amountMinor, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":%d}".formatted(target, amountMinor)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void anHonestRetryReplaysTheFirstResponseWithoutReExecuting() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 100_000L);
        long before = statementTotal(alice[0]);

        String key = uniqueIdempotencyKey();
        String payload = "{\"targetAccountNumber\":\"%s\",\"amountMinor\":25000}".formatted(bob[1]);

        String first = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String second = transfer(alice[0], bob[1], 25_000L, key);

        assertThat(objectMapper.readTree(first).get("id").asLong())
                .isEqualTo(objectMapper.readTree(second).get("id").asLong());

        long after = statementTotal(alice[0]);
        assertThat(after).isEqualTo(before + 1); // exactly one transfer: only alice's DEBIT leg is on her statement

        // balance debited exactly once despite the retry
        MvcResult balance = mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", "Bearer " + alice[0]))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(balance.getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("balanceMinor").asLong()).isEqualTo(75_000L);
    }

    @Test
    void keyReuseWithADifferentPayloadIsRejected() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 100_000L);

        String key = uniqueIdempotencyKey();
        transfer(alice[0], bob[1], 10_000L, key);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":20000}".formatted(bob[1])))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void keysAreScopedPerEndpoint() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 100_000L);

        String key = uniqueIdempotencyKey();
        transfer(alice[0], bob[1], 10_000L, key);

        mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":10000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void missingOrMalformedKeysAreRejectedBeforeAnyMoneyMoves() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":1000}".formatted(bob[1])))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", "too-short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":1000}".formatted(bob[1])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"));

        assertThat(statementTotal(alice[0])).isEqualTo(0);
    }

    @Test
    void concurrentDuplicatesRunTheOperationAtMostOnce() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 100_000L);
        long before = statementTotal(alice[0]);

        String key = uniqueIdempotencyKey();
        String payload = "{\"targetAccountNumber\":\"%s\",\"amountMinor\":5000}".formatted(bob[1]);
        var requestBuilder = post("/api/v1/transfers")
                .header("Authorization", "Bearer " + alice[0])
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> send = () -> mockMvc.perform(requestBuilder).andReturn().getResponse().getStatus();
            Future<Integer> first = pool.submit(send);
            Future<Integer> second = pool.submit(send);
            int statusA = first.get(30, TimeUnit.SECONDS);
            int statusB = second.get(30, TimeUnit.SECONDS);
            pool.shutdown();

            // Either a replay (201/201) or one run plus a 409 "in progress" — never two runs.
            assertThat(statusA).isIn(201, 409);
            assertThat(statusB).isIn(201, 409);
            assertThat(statementTotal(alice[0])).isEqualTo(before + 1);
        }
    }
}
