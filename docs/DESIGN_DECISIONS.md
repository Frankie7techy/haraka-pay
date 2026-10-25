# Design decisions

Short ADRs explaining the choices a reviewer is most likely to question.

## ADR-001: Money is integer minor units, never floating point

**Decision.** Amounts are `BIGINT` minor units (`KES 1.00 == 100`), converted to display
strings (`Amounts.formatMinor`) only at the API boundary.

**Why.** Binary floating point cannot represent 0.10 exactly; `BigDecimal` with a fixed scale
is workable but invites drift between services. Integer minor units are what card networks and
mobile-money platforms actually settle in.

## ADR-002: Double-entry ledger + mutable balances

**Decision.** Every money movement writes one immutable `ledger_entry` per touched account
(DEBIT on source, CREDIT on target) with a `balance_after_minor` snapshot. Wallet balances are
mutable columns updated in the same transaction.

**Why.** Balances give O(1) reads; the ledger gives replayability and audit. Neither is trusted
alone: `LedgerIntegrityHealthIndicator` recomputes every balance from the ledger on every health
check and fails `/actuator/health` (503) on any divergence. In production this is the hook where
a nightly reconciliation job would live.

## ADR-003: Two independent concurrency defences + bounded retry

**Decision.** Transfers lock both wallets with `SELECT ... FOR UPDATE` in ascending-id order
(`WalletAccountRepository.findAllForUpdate`), which structurally prevents the A→B / B→A deadlock.
An optimistic `version` column backstops the lock, and `OptimisticRetryExecutor` retries
optimistic-lock failures up to 8 times with exponential backoff before answering `503 CONTENTION`.

**Why.** The version column caught a real failure mode during development: H2 in PostgreSQL mode
(the test/CI database) hands a waiting `FOR UPDATE` query a snapshot row, so a contender could
lose the version check even with correct lock ordering. PostgreSQL re-reads the latest committed
row after acquiring the lock, so the lock alone suffices there — but the retry makes the behaviour
correct and identical on both engines. Details in `CHALLENGE_JOURNAL.md`.

## ADR-004: Idempotency as a reusable wrapper, not per-controller logic

**Decision.** Every money-mutating endpoint takes an `Idempotency-Key` (16–64 chars,
URL-safe) and goes through `IdempotentRequests`, which hashes the canonical payload plus the
endpoint (SHA-256), reserves the key with a unique-constrained `IN_FLIGHT` marker, executes,
then persists `(status, body)` for exact replays.

**Why.** Payment rails charge you for duplicate submissions; the retry may come from a flaky
mobile connection seconds later. Semantics:

| State for (user, key) | Response |
|---|---|
| no record | execute, store response |
| same key, same endpoint, same hash | replay stored response verbatim |
| same key, different hash or endpoint | `409 IDEMPOTENCY_CONFLICT` |
| same key, still in flight | `409` (concurrent duplicate rejected) |
| operation failed | marker deleted, key free to retry |

**Trade-off documented:** if the process dies between reserving and completing, the marker stays
`IN_FLIGHT` until retention cleanup (7 days, `IdempotencyCleanupJob`) — the retry then gets a 409
instead of a double execution, which is the safe failure direction for money.

## ADR-005: Schema is owned by Flyway, Hibernate never generates DDL

**Decision.** `ddl-auto: none` in every profile; migrations are hand-written SQL that runs
identically on PostgreSQL and H2-in-PostgreSQL-mode (identity columns, no vendor types).

**Why.** Migration history becomes reviewable, reproducible evidence that the test database and
the production database are the same schema. Tests run the real migrations on every CI build.

## ADR-006: Aggregates reference each other by id, not by JPA associations

**Decision.** `Transfer` and `LedgerEntry` store account ids (`source_account_id`), not
`@ManyToOne WalletAccount` associations.

**Why.** Cross-aggregate object graphs are the main source of N+1 queries and lazy-loading
surprises. Explicit id references make every query intentional and keep aggregate boundaries
honest. The cost is manual joins when a view needs both sides — which is exactly one repository
call here.

## ADR-007: RFC 7807 problems with stable codes everywhere

**Decision.** `GlobalExceptionHandler` converts every failure (business, validation, framework,
unknown) into a problem document carrying a machine-readable `code`
(`INSUFFICIENT_FUNDS`, `IDEMPOTENCY_CONFLICT`, `VALIDATION_FAILED`, ...). Security failures use
the same shape via the authentication entry point and access-denied handler.

**Why.** Clients branch on semantics, not on parsing messages. It also keeps stack traces out of
responses while logging the full cause server-side with a correlation id.

## ADR-008: Existence is not disclosed across customers

**Decision.** Reading (or listing entries of) a transfer the caller did not participate in
returns `404 NOT_FOUND`, not `403`.

**Why.** A 403 confirms the transfer exists; a 404 does not. Authenticated but unauthorised
knowledge should not leak.
