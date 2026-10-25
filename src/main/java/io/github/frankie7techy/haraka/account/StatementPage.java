package io.github.frankie7techy.haraka.account;

import java.util.List;

/** Manual paging envelope (page/size are explicit request parameters, not Spring's page object). */
public record StatementPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<LedgerEntryView> entries) {
}
