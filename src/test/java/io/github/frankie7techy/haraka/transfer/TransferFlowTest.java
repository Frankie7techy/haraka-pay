package io.github.frankie7techy.haraka.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.frankie7techy.haraka.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class TransferFlowTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String topup(String token, long amountMinor) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":%d,\"narrative\":\"seed money\"}".formatted(amountMinor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String balanceMinorOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("balanceMinor").asText();
    }

    @Test
    void movesMoneyAtomicallyAndLeavesTwoLedgerLegs() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 500_000L);

        String transferId = objectMapper.readTree(mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + alice[0])
                                .header("Idempotency-Key", uniqueIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":200000,\"narrative\":\"lunch\"}"
                                        .formatted(bob[1])))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.amount").value("2000.00"))
                        .andExpect(jsonPath("$.sourceAccountNumber").value(alice[1]))
                        .andExpect(jsonPath("$.targetAccountNumber").value(bob[1]))
                        .andReturn().getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("id").asText();

        assertThat(balanceMinorOf(alice[0])).isEqualTo("300000"); // 5000.00 - 2000.00
        assertThat(balanceMinorOf(bob[0])).isEqualTo("200000");   // 0.00 + 2000.00

        mockMvc.perform(get("/api/v1/transfers/{id}/entries", transferId)
                        .header("Authorization", "Bearer " + alice[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$[0].balanceAfter").value("3000.00"))
                .andExpect(jsonPath("$[1].direction").value("CREDIT"))
                .andExpect(jsonPath("$[1].balanceAfter").value("2000.00"));

        // The statement (audit trail) records both legs, newest first
        mockMvc.perform(get("/api/v1/accounts/me/statement").param("size", "10")
                        .header("Authorization", "Bearer " + alice[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"));
    }

    @Test
    void transfersAreOnlyVisibleToParticipants() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        String[] mallory = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 100_000L);

        String transferId = objectMapper.readTree(mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + alice[0])
                                .header("Idempotency-Key", uniqueIdempotencyKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":10000}"
                                        .formatted(bob[1])))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("id").asText();

        mockMvc.perform(get("/api/v1/transfers/{id}", transferId)
                        .header("Authorization", "Bearer " + mallory[0]))
                .andExpect(status().isNotFound()); // 404, not 403: no existence leak

        mockMvc.perform(get("/api/v1/transfers/{id}", transferId)
                        .header("Authorization", "Bearer " + bob[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(transferId)));
    }

    @Test
    void insufficientFundsFailWithAuditTrailButNoMoneyMovement() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());
        topup(alice[0], 50_000L);

        long failedBefore = countFailedTransfers();
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":900000}".formatted(bob[1])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("500.00")));
        long failedAfter = countFailedTransfers();

        assertThat(failedAfter).isEqualTo(failedBefore + 1); // rejected instruction is auditable
        assertThat(balanceMinorOf(alice[0])).isEqualTo("50000"); // nothing moved
        assertThat(balanceMinorOf(bob[0])).isEqualTo("0");

        // statement untouched: failed transfers never post ledger rows
        mockMvc.perform(get("/api/v1/accounts/me/statement")
                        .header("Authorization", "Bearer " + alice[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)); // only the topup
    }

    @Test
    void businessRuleViolationsCarryStableProblemCodes() throws Exception {
        String[] alice = registerUserWithAccount(uniqueEmail());
        String[] bob = registerUserWithAccount(uniqueEmail());

        // over the per-transfer ceiling -> policy, not validation
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":50000001}".formatted(bob[1])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        // self transfer
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"%s\",\"amountMinor\":100}".formatted(alice[1])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSFER"));

        // unknown account
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"HAR-999999\",\"amountMinor\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // bean validation failures list the offending fields
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice[0])
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountNumber\":\"BOGUS\",\"amountMinor\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.targetAccountNumber").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.amountMinor").isNotEmpty());
    }

    private long countFailedTransfers() {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from transfer where status = 'FAILED'", Long.class);
        return count == null ? 0L : count;
    }
}
