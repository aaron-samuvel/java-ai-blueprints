# Examples

## Wire the advisor
```java
TokenBudgetLedger ledger = new TokenBudgetLedger(100_000);   // default per-tenant limit
ledger.setLimit("acme", 1_000_000);
FinOpsBudgetAdvisor finops = new FinOpsBudgetAdvisor(ledger, new TokenEstimator(), 512, PriceList.FREE);

ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(piiAdvisor, finops)                  // PII first, then budget
        .build();

client.prompt().user("Summarise this ticket")
      .advisors(a -> a.param(FinOpsBudgetAdvisor.TENANT_KEY, "acme"))
      .call().content();

ledger.snapshot("acme");   // Snapshot[tenant=acme, limit=1000000, reserved=0, used=…]
finops.records();          // per-call UsageRecord audit trail
```

## Runnable demo
`FinOpsDemo` (in `src/main`) gives a tenant a 300-token budget and makes calls until one is blocked.
Run it from your IDE, or with the `exec:java` command in the module README.

Actual output from a run on the build box (17 Oct 2026), last lines:
```
call 10 ok   Snapshot[tenant=default, limit=300, reserved=0, used=220]
call 11 ok   Snapshot[tenant=default, limit=300, reserved=0, used=242]
call 12 BLOCKED: token budget exceeded for tenant 'default': requested 62, available 58
```
