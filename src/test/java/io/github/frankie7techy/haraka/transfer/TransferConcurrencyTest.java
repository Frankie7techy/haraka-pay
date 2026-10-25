package io.github.frankie7techy.haraka.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.frankie7techy.haraka.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The regression test for the double-spend race. With a deterministic lock order the
 * competing transfers serialize and the total money in the system never changes; without
 * it, concurrent debits used to read the same balance and both succeed.
 */
class TransferConcurrencyTest extends IntegrationTestBase {

    private static final int TRANSFERS_PER_DIRECTION = 15;
    private static final long TRANSFER_AMOUNT = 10_000L; // KES 100.00

    @Test
    void opposingTransfersSerializeWithoutLostUpdatesOrDeadlocks() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        long openingBalance = 1_000_000L; // KES 10,000.00 each

        topup(alice[0], openingBalance);
        topup(bob[0], openingBalance);

        List<Integer> statusCodes = runConcurrentTransfers(alice[0], bob[1], bob[0], alice[1]);

        // every instruction completed; none was rejected for locking reasons
        assertThat(statusCodes).hasSize(30).containsOnly(201);

        long aliceFinal = balanceMinor(alice[0]);
        long bobFinal = balanceMinor(bob[0]);

        // 15 x 100 KES out and 15 x 100 KES in for each side -> opening balance exactly
        assertThat(aliceFinal).isEqualTo(openingBalance);
        assertThat(bobFinal).isEqualTo(openingBalance);

        // the hard invariant: no money was created or destroyed
        assertThat(aliceFinal + bobFinal).isEqualTo(openingBalance * 2);
    }

    private List<Integer> runConcurrentTransfers(String aliceToken, String bobAccount,
                                                 String bobToken, String aliceAccount) throws Exception {
        record Instruction(String token, String target) {
        }
        record HttpResult(int status, String body) {
        }

        List<Instruction> instructions = new ArrayList<>();
        for (int i = 0; i < TRANSFERS_PER_DIRECTION; i++) {
            instructions.add(new Instruction(aliceToken, bobAccount));
            instructions.add(new Instruction(bobToken, aliceAccount));
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<HttpResult>> futures = new ArrayList<>();
            for (Instruction instruction : instructions) {
                futures.add(pool.submit((Callable<HttpResult>) () -> {
                    var response = mockMvc.perform(post("/api/v1/transfers")
                                    .header("Authorization", "Bearer " + instruction.token())
                                    .header("Idempotency-Key", uniqueIdempotencyKey())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":%d,\"narrative\":\"load\"}"
                                            .formatted(instruction.target(), TRANSFER_AMOUNT)))
                            .andReturn().getResponse();
                    return new HttpResult(response.getStatus(),
                            response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                }));
            }
            List<HttpResult> results = new ArrayList<>();
            for (Future<HttpResult> future : futures) {
                results.add(future.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            pool.shutdown();

            List<HttpResult> failures = results.stream().filter(r -> r.status() != 201).toList();
            assertThat(failures).withFailMessage(() -> "unexpected responses: "
                    + failures.stream().limit(3)
                    .map(r -> r.status() + " -> " + r.body())
                    .toList() + " (sample of " + failures.size() + ")").isEmpty();
            return results.stream().map(HttpResult::status).toList();
        }
    }

    private void topup(String token, long amountMinor) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":%d}".formatted(amountMinor)))
                .andExpect(status().isCreated());
    }

    private long balanceMinor(String token) throws Exception {
        return Long.parseLong(objectMapper.readTree(mockMvc
                .perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("balanceMinor").asText());
    }
}
