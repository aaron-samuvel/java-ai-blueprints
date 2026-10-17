# Theory: GOAP inside an OODA loop

* **World state:** a set of string facts, such as `billingObserved`, `cause:IDLE_RESOURCES` or `approvalGranted`.
* **Action:** has preconditions (facts that must hold), `unless` facts (facts that must *not* hold), add and remove effects, and a cost. A* searches over world states. The heuristic is the number of unmet goal facts. That heuristic is admissible when every action costs at least 1.
* **Sensing actions** such as `diagnose` and `requestApproval` declare *optimistic* effects so the planner can plan through them. At runtime the real result replaces the declared one (`applyDeclared=false`). This is why the agent re-plans every cycle.
* **Negative preconditions** (`unless("approvalDecided")`) stop the agent from asking for approval again after a denial. Without them the loop never ends. This was a real bug found by the tests.
* **Escalation** is always reachable but costs 10. The cheap automated paths win when they are possible, and a human gets the incident when they are not.
* The **LLM** is used only for Orient: it classifies the cause into a structured entity. If the answer can't be parsed, the cause becomes `UNKNOWN`, which leads to escalation. The model never chooses actions directly. That keeps the agent auditable and makes its cost bounded (at most 10 cycles).
