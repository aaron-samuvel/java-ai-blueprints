# Exercises: Blueprint 04

*(author's design, not from the handbook)*

1. Add Postgres row-level security under `-Pit` and show a query without `tenant_id` returns nothing.
2. Write the outbox relay: a scheduled job that "sends" outbox rows and marks them sent.
3. Shorten the transaction: call the models outside it, then persist memory and rows in one short `@Transactional` step. Keep the rollback tests green.
4. Add a fourth agent, "fraud-check", that can veto refunds above INR 2000. Test the veto rolls back.
5. Take the tenant from a JWT using Spring Security's resource server and a test JWT.
