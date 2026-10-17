# AGENTS.md: repository constitution

> Based on handbook Part IV ("AGENTS.md constitution"). The specific clauses below are
> **author's design (not from handbook)**. My notes record that Part IV prescribes an AGENTS.md
> constitution, but not its exact wording.

This file tells AI coding agents (and people) how to work in this repository.

## 1. Mission
Ship small, verifiable, enterprise-grade Java AI blueprints that run offline by default.

## 2. Non-negotiables
1. **No secrets in code or history.** Read config from env vars (`OLLAMA_BASE_URL`, `PGPASSWORD`, …).
2. **Zero-trust data.** Raw PII must never reach a model or a log. Route prompts through
   `PiiTokenizationAdvisor` (Blueprint 05) or an equivalent advisor.
3. **Records are contracts.** Model inputs and outputs that code depends on are Java records with validation.
4. **Every model call goes through an Advisor chain.** Put guardrails, budgets and auditing there,
   not scattered through business code.
5. **Hermetic tests.** `mvn verify` passes with no network, Docker or LLM. Live tests are `*IT`, under `-Pit`.
6. **Budgets before calls.** Reserve, call, then commit (FinOps pillar). Never call a paid model without a budget.
7. **Evaluation gates.** A change to prompts or RAG must keep the evaluation tests green.
8. **No fabrication.** Never claim a test passed, a push succeeded or a URL exists without checking.

## 3. Build & test
```bash
mvn -B verify          # offline, required before every commit
mvn -B -Pit verify     # needs local Ollama; optional
```

## 4. Layout
One folder per handbook topic: `README.md`, `theory.md`, `examples/`, `exercises.md`, `src/main`, `src/test`.
Shared test doubles live in `shared/`. Don't copy them into modules.

## 5. Style
Java 21, records, sealed types and pattern matching where they help; virtual threads for blocking I/O;
constructor injection; no field injection; no Lombok.

## 6. Git
Conventional Commits. No force-push to `main`. No rewriting published history.

## 7. When unsure
Stop and ask. Document assumptions in the module README under "Assumptions".
