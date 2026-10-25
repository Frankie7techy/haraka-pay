package io.github.frankie7techy.haraka.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.frankie7techy.haraka.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthFlowTest extends IntegrationTestBase {

    @Test
    void registersAUserAndOpensTheirWallet() throws Exception {
        performRegister(uniqueEmail())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").isNotEmpty())
                .andExpect(jsonPath("$.account.accountNumber").value(
                        org.hamcrest.Matchers.matchesPattern("HAR-\\d{6,12}")))
                .andExpect(jsonPath("$.account.balance").value("0.00"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"));
    }

    @Test
    void rejectsDuplicateRegistrationWithAConflict() throws Exception {
        String email = uniqueEmail();
        performRegister(email).andExpect(status().isCreated());
        performRegister(email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsWeakPasswordsBeforeTouchingTheService() throws Exception {
        String body = """
                {"email":"%s","fullName":"Test User","password":"short"}
                """.formatted(uniqueEmail());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    void logsInWithCorrectCredentials() throws Exception {
        String email = uniqueEmail();
        performRegister(email).andExpect(status().isCreated());

        String body = """
                {"email":"%s","password":"Sup3rSecret!x"}
                """.formatted(email);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginFailureNeverRevealsWhetherTheEmailExists() throws Exception {
        String wrongPassword = """
                {"email":"%s","password":"Wr0ngPassword!"}
                """.formatted(uniqueEmail());
        String unknownEmail = """
                {"email":"ghost-%s","password":"Sup3rSecret!x"}
                """.formatted(uniqueEmail());

        for (String body : new String[]{wrongPassword, unknownEmail}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
        }
    }

    @Test
    void protectedEndpointsRejectMissingAndGarbageTokens() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.type").value("about:blank"));

        mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointStaysPublicForLoadBalancers() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void balanceIsZeroRightAfterRegistration() throws Exception {
        String token = registerUser(uniqueEmail());
        mockMvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(0))
                .andExpect(jsonPath("$.currency").value("KES"));
        assertThat(token).isNotBlank();
    }
}
