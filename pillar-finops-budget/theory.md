# Theory: reserve, call, commit

The handbook names a "FinOps reserve-call-commit budget advisor". The details below are
**author's design (not from handbook)**.

## Why not just count after the call?
Counting afterwards tells you that you overspent, but doesn't stop it. With many concurrent
requests, each one sees "budget left" and they all go through. **Reserving** the worst case first
(like a card pre-authorisation) makes the check-and-hold step atomic, so the sum of in-flight
reservations plus committed usage can never exceed the limit. The concurrency test proves this:
1,000 parallel requests of 30 tokens against 10,000 grant exactly 333.

## Estimate vs actual
| Phase | Number | Source |
|---|---|---|
| Reserve | `ceil(promptTokens × 1.2) + maxOutputTokens` | Local BPE count; worst-case output |
| Commit | prompt + completion tokens | Provider `Usage` metadata if present, otherwise a local count of the prompt and answer |

The difference between them is given back at commit. If the actual exceeds the estimate (a model
ignored `maxTokens`), it's still charged, because the tokens were spent, and the next reservation fails sooner.

## Place in the advisor chain
`PiiTokenizationAdvisor (HIGHEST_PRECEDENCE)` → **`FinOpsBudgetAdvisor (HIGHEST_PRECEDENCE+10)`** → memory, RAG and others → model.

The budget therefore counts the tokenised prompt that is really sent. One caveat: RAG context added
by advisors *after* it isn't in the estimate. Provider usage at commit still captures it, so either
put RAG before FinOps or raise `maxOutputTokens`.

## Failure semantics
- Over budget: throw `BudgetExceededException` and **don't call the model**. Map it to HTTP 429 in the web layer.
- Provider error: release the reservation, so no budget leaks.
- A double commit is an error, so the same call can't be charged twice.
