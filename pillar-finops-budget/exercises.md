# Exercises: FinOps budget pillar

*(author's design, not from the handbook)*

1. Add a monthly window: budgets reset on the 1st (Asia/Kolkata). Inject a `Clock` so it's testable.
2. Put the ledger in Postgres with `SELECT … FOR UPDATE`, and re-run the concurrency test under `-Pit`.
3. Add a `@ControllerAdvice` that maps `BudgetExceededException` to HTTP 429 with a `Retry-After` header.
4. Chain it with Blueprint 05's PII advisor and show the budget is charged for the *tokenised* prompt.
5. Add a soft limit at 80% that logs a warning event while still allowing the call.
