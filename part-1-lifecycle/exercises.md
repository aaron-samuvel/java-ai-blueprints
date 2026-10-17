# Exercises: Part I

*(author's design, not from the handbook)*

1. **Tighter contract.** Add `Instant detectedAt` and an `int confidence` (0–100) to `IncidentTriage`.
   Reject out-of-range values. Add a test where the stub returns `confidence: 140`.
2. **Token-budget advisor.** Copy `AuditTrailAdvisor` into a `MaxPromptCharsAdvisor` that throws
   if the prompt is longer than N characters. Test it with `StubChatModel`.
3. **Ordering.** Register two advisors with orders 0 and 10. Prove which one sees the request first.
4. **Virtual threads at scale.** Fan out 10,000 calls to a stub that sleeps 50 ms. Compare the wall
   time with `Executors.newFixedThreadPool(50)`.
5. **Live model.** With Ollama running, swap `StubChatModel` for `OllamaChatModel`. How often does
   `llama3.2:3b` produce valid `IncidentTriage` JSON in 20 tries?
