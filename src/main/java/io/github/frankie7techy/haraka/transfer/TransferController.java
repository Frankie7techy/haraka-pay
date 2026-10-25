package io.github.frankie7techy.haraka.transfer;

import io.github.frankie7techy.haraka.account.LedgerEntryView;
import io.github.frankie7techy.haraka.idempotency.IdempotentRequests;
import io.github.frankie7techy.haraka.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Idempotent P2P transfers over the double-entry ledger")
public class TransferController {

    private static final String ENDPOINT = "POST /api/v1/transfers";

    private final TransferService transferService;
    private final IdempotentRequests idempotentRequests;

    public TransferController(TransferService transferService, IdempotentRequests idempotentRequests) {
        this.transferService = transferService;
        this.idempotentRequests = idempotentRequests;
    }

    @PostMapping
    @Operation(summary = "Create a P2P transfer (idempotent via the Idempotency-Key header)")
    public ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal UserPrincipal caller,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody NewTransferRequest request) {
        return idempotentRequests.execute(ENDPOINT, caller, idempotencyKey, request,
                201, TransferResponse.class,
                () -> transferService.transfer(caller, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transfer the caller participated in")
    public TransferResponse get(@AuthenticationPrincipal UserPrincipal caller, @PathVariable long id) {
        return transferService.get(caller, id);
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "Ledger legs (DEBIT and CREDIT) of a transfer")
    public List<LedgerEntryView> entries(@AuthenticationPrincipal UserPrincipal caller, @PathVariable long id) {
        return transferService.entries(caller, id);
    }
}
