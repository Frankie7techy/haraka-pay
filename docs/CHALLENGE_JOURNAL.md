# Challenge journal

Written in the form problem → hypothesis → fix → verification. This is the material the
3-minute problem-solving video walks through.

## Challenge 1: Concurrent transfers double-spent the same balance

**Symptom.** Load-testing two wallets with opposing transfers (A→B and B→A at the same time)
produced HTTP 500s for ~70% of requests. The surviving transfers were correct, so sequential
testing had never caught anything.

**Diagnosis.** The server log showed
`ObjectOptimisticLockingFailureException: Row was updated or deleted by another transaction
(WalletAccount#1)`. Reading the code path: both requests locked the two accounts with
`SELECT ... FOR UPDATE` in ascending-id order — the textbook deadlock-free pattern. So why did
the version check fail?

The subtle part: the test/CI database is **H2 in PostgreSQL compatibility mode** (so CI can run
the real Flyway migrations without Docker). H2 hands a waiting `FOR UPDATE` query the *snapshot*
row it captured before blocking, while PostgreSQL re-reads the *latest committed* row version
after acquiring the lock. With H2 semantics, a contender could acquire the lock, receive a stale
`version`, and fail its own `UPDATE ... WHERE version = ?`.

**Fix (three layers, each defensible on its own):**
1. Keep the deterministic `FOR UPDATE` lock order — it remains the primary serialisation on
   PostgreSQL and prevents deadlocks on any engine.
2. Keep the `@Version` column — it converts any residual race into a rejected flush instead of a
   silent lost update (the reason money was never actually created or destroyed).
3. Add `OptimisticRetryExecutor`: re-run the whole transactional unit on optimistic-lock failure,
   up to 8 attempts with exponential backoff, then return a clean `503 CONTENTION` instead of a 500.

**Verification.** `TransferConcurrencyTest` fires 30 opposing transfers from 8 threads against
H2. It asserts every request returns 201, both wallets end exactly at their opening balances, and
the sum of money is conserved. It also fails with the offending response bodies in the assertion
message if anything other than 201 appears. The suite was deliberately run repeatedly to check
for flakiness; retries converge because each attempt re-reads committed state.

**Lesson.** A concurrency strategy that only works on one database is not a strategy. Decide what
the correct behaviour is under contention, then make every engine in your test matrix agree with
it — or make the application code enforce the agreement itself.

## Challenge 2: The idempotency key race (duplicate in-flight requests)

**Symptom.** While building replay protection, two identical requests arriving milliseconds apart
(the classic mobile-network retry) could both pass the "check for existing record" step, because
neither had committed its reservation yet.

**Fix.** The reservation is an `INSERT` guarded by a unique constraint on `(user_id,
idempotency_key)` in its own `REQUIRES_NEW` transaction. The loser of the insert race re-reads:
if the winner is still `IN_FLIGHT` it gets `409 "already in progress"`; if the winner already
finished it gets the stored response — a replay, not a re-execution. Failed operations delete the
marker so genuine failures stay retryable.

**Verification.** `TransferIdempotencyTest.concurrentDuplicatesRunTheOperationAtMostOnce` fires
the same payload twice concurrently from two threads; the statement is asserted to contain
exactly one debit leg and the result statuses are constrained to `{201, 409}`.

## Challenge 3: Audit rows that vanished with the rollback

**Symptom.** A rejected transfer (insufficient funds) was supposed to be recorded for audit, but
the `transfer` row marked `FAILED` disappeared whenever the exception rolled the transaction back.

**Fix.** Rejected instructions are persisted by `TransferAudit.recordFailed` in a separate
`REQUIRES_NEW` transaction, so the audit row survives the business transaction's rollback. The
component only touches the `transfer` table, never the locked `wallet_account` rows, so it cannot
deadlock against the caller's locks.

**Verification.** `TransferFlowTest.insufficientFundsFailWithAuditTrailButNoMoneyMovement` counts
`FAILED` transfers before/after the request and asserts balances and statement contents are
untouched.

## Challenge 4: Every failure used to look different

**Symptom.** Before the API hardened, validation failures returned Spring's default error JSON,
security failures another shape, and business failures a third — painful for any client.

**Fix.** One `@RestControllerAdvice` maps every failure to RFC 7807 with a stable `code`;
the security filter chain's entry point and access-denied handler emit the same shape. Header
binding failures (`MissingRequestHeaderException`), malformed bodies, and unknown exceptions each
have explicit handlers so nothing accidentally becomes a 500.

**Verification.** `AuthFlowTest` and `TransferFlowTest` assert on `$.code` for a dozen distinct
failure modes.
