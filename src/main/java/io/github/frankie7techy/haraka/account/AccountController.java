package io.github.frankie7techy.haraka.account;

import io.github.frankie7techy.haraka.idempotency.IdempotentRequests;
import io.github.frankie7techy.haraka.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Wallet balance, statement and simulated deposits")
public class AccountController {

    private final AccountService accountService;
    private final IdempotentRequests idempotentRequests;

    public AccountController(AccountService accountService, IdempotentRequests idempotentRequests) {
        this.accountService = accountService;
        this.idempotentRequests = idempotentRequests;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the caller's wallet balance and status")
    public AccountView me(@AuthenticationPrincipal UserPrincipal caller) {
        return accountService.currentAccount(caller);
    }

    @GetMapping("/me/statement")
    @Operation(summary = "Paged ledger statement for the caller's wallet (newest first)")
    public StatementPage statement(@AuthenticationPrincipal UserPrincipal caller,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return accountService.statement(caller, page, size);
    }

    @PostMapping("/me/topups")
    @Operation(summary = "Simulate an M-Pesa-style deposit into the caller's wallet")
    public ResponseEntity<LedgerEntryView> topup(
            @AuthenticationPrincipal UserPrincipal caller,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TopupRequest request) {
        return idempotentRequests.execute("POST /api/v1/accounts/me/topups", caller, idempotencyKey, request,
                201, LedgerEntryView.class,
                () -> accountService.topup(caller, request));
    }
}
