package io.github.frankie7techy.haraka.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.frankie7techy.haraka.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves the reconciliation invariant the health indicator guards: balance must equal
 * the ledger net. Corrupting a balance (simulating an out-of-band UPDATE, the classic
 * way ledgers get out of sync) must turn the whole health endpoint red.
 */
class LedgerIntegrityHealthTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportsUpWhenBalancesMatchTheLedger() throws Exception {
        String token = registerUser(uniqueEmail());
        mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":70000}"))
                .andExpect(status().isCreated());

        String accountNumber = accountNumberOf(token);
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.ledgerIntegrity.status").value("UP"))
                .andExpect(jsonPath("$.components.ledgerIntegrity.details.accountsChecked")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        assertThat(accountNumber).startsWith("HAR-");
    }

    @Test
    void flipsToDownWhenABalanceDivergesFromTheLedger() throws Exception {
        String token = registerUser(uniqueEmail());
        mockMvc.perform(post("/api/v1/accounts/me/topups")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", uniqueIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":30000}"))
                .andExpect(status().isCreated());

        String accountNumber = accountNumberOf(token);
        jdbcTemplate.update(
                "update wallet_account set balance_minor = balance_minor + 1 where account_number = ?",
                accountNumber); // the classic out-of-band write

        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andReturn();
        JsonNode health = objectMapper.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(health.get("status").asText()).isEqualTo("DOWN");
        assertThat(health.get("components").get("ledgerIntegrity").get("status").asText()).isEqualTo("DOWN");
        assertThat(health.get("components").get("ledgerIntegrity").get("details")
                .get("mismatchedAccounts").toString()).contains(accountNumber);

        // restore so the shared test context stays healthy for other tests
        jdbcTemplate.update(
                "update wallet_account set balance_minor = balance_minor - 1 where account_number = ?",
                accountNumber);
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String accountNumberOf(String token) throws Exception {
        return objectMapper.readTree(mockMvc
                        .perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .get("accountNumber").asText();
    }
}
