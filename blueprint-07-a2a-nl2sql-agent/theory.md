# Theory: guarded, self-healing NL2SQL and A2A

The handbook specifies: A2A mesh, self-healing NL2SQL agent, JSqlParser guard, `LIMIT 500`,
3 retries. The reasoning below is **author's design (not from handbook)**.

## Why parse SQL instead of using regex or "please only SELECT" prompts
Prompts are suggestions; the model can ignore them. Regexes miss things like
`SELECT 1; DROP TABLE x`, comments, or `WITH … DELETE`. JSqlParser builds an AST, so the guard can check:
1. **Statement count = 1.** No stacked statements.
2. **Statement type = `Select`.** No DML or DDL, no `SELECT … INTO`, no `FOR UPDATE`.
3. **Tables ⊆ allow-list.** No `information_schema`, no unexpected tables.
4. **Row cap:** `LIMIT` is set to `min(existing, 500)`, or added if missing.
   The re-serialised AST is what actually runs, not the model's raw text.

## Defence in depth
| Layer | Stops |
|---|---|
| JSqlParser guard | Writes, DDL, stacked statements, foreign tables, unbounded results |
| `JdbcTemplate.setMaxRows(500)` | Anything that slips past the LIMIT rewrite |
| Query timeout (10 s) | Expensive cartesian joins |
| Read-only DB user (production) | Everything else, enforced by the database itself |

## Self-healing loop
Small models often get column names wrong. Instead of failing, the agent sends back:
- the original question;
- the SQL that failed;
- the **exact** guard or database error.

It does this at most 3 times. The guard's error messages are written so a model can act on them
(for example "table not allowed: PAYROLL"). The `Nl2SqlResult` records every error, which helps with evaluation.

## A2A (Agent-to-Agent)
A2A lets agents discover each other through an **agent card** (name, skills, endpoint) and delegate
work as **tasks**. This blueprint exposes:
- `GET /.well-known/agent.json`: the card, listing the `nl2sql` skill;
- `POST /a2a/tasks`: runs the skill and returns `COMPLETED` or `FAILED`, with the SQL, rows, attempt count and errors as the artifact.

That's enough for an orchestrator agent to call it. The official protocol (JSON-RPC, streaming,
task lifecycle) is broader, and moving to it is planned.
