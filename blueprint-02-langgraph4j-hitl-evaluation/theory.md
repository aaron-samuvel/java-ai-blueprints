# Theory: stateful agent graphs with a human in the loop

The handbook names "LangGraph4j HITL multi-agent evaluation". The explanation is **author's design
(not from handbook)**.

## Why a graph?
A chain is linear. Real review workflows **loop** (revise until it's good), **branch** (pass or fail),
and **wait** (for a person, possibly for days). LangGraph4j models this as a `StateGraph`:
- **Nodes** are functions `state -> partial update`. Here they're the drafter, evaluator, human_review, publish and rejected nodes.
- **Edges** can be fixed or *conditional*: a function of the state picks the next node.
- **Channels** define how updates merge. `history` uses an *appender* channel; other keys are last-write-wins.

## Checkpoints and interrupts
With a checkpoint saver, the state is saved after every node, per **thread id**.
`interruptBefore("human_review")` stops execution before that node and returns control to the caller.
The human's decision is written with `updateState(config, {humanDecision: …})`, and
`invoke(GraphInput.resume(), config)` continues from the checkpoint. That's how a web request
today and an approval tomorrow can belong to the same run.

## Multi-agent roles
| Agent | Job | Model |
|---|---|---|
| Drafter | Write a grounded one-sentence answer, and revise using feedback | `ChatModel` (stub / Ollama) |
| Evaluator | `RelevancyEvaluator` + `FactCheckingEvaluator` via `EvaluationGate` | Judge model (lexical stub / Ollama) |
| Human | Final approve/reject, with a free-text comment | a person |

Putting the automatic evaluator *before* the human saves reviewer time: obviously bad drafts are
fixed or escalated, and humans only see candidates that passed or that the machine gave up on.
