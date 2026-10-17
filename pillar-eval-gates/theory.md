# Theory: evaluation gates

The handbook lists "JUnit evaluation gates (RelevancyEvaluator, FactCheckingEvaluator)" in Part IV
and "dual-layer verification" as J-AIDLC stage 4. The design below is **author's design (not from handbook)**.

## Dual-layer verification
1. **Deterministic layer:** unit tests with stub models check the plumbing, for example
   "PII never reaches the model" or "LIMIT 500 is enforced". They're exact and fast.
2. **Semantic layer:** evaluators judge the *content* of answers. That's inherently fuzzy, so it's
   expressed as pass-rate thresholds over a golden set, not exact assertions.

## The two Spring AI evaluators
| Evaluator | Asks the judge | Catches |
|---|---|---|
| `RelevancyEvaluator` | "Is the response for the query in line with the context?" (YES/NO) | Off-topic or ungrounded answers |
| `FactCheckingEvaluator` | "Is the claim supported by the document?" (yes/no) | Hallucinated facts, wrong numbers |

Both are ordinary Spring AI classes driven by a `ChatClient`, so **any `ChatModel` can be the judge**.

## Why a deterministic judge offline?
A CI gate that calls a real LLM is slow, costs money or needs a GPU, and is flaky. `LexicalJudgeChatModel`:
- parses the evaluator's own prompt (`Response:`/`Context:` or `Document:`/`Claim:`);
- answers YES when at least 70% of the claim's content words (numbers included) appear in the evidence.

It's dumb on purpose. It can't recognise paraphrases, but it reliably catches invented facts and
changed numbers, which are the most common RAG regressions. The `-Pit` profile then runs the same
gate with Ollama as judge, for semantic nuance.

## Thresholds
- Offline: 90%, because the judge is deterministic.
- Live: 60%, because a 3B judge is noisy.

Raise the live threshold, or switch to a stronger judge (for example, a fact-checking-tuned model),
before relying on it in CI.
