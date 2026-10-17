# Exercises: Blueprint 07

*(author's design, not from the handbook)*

1. Allow `WITH` (CTE) queries but still block `WITH x AS (…) DELETE …`. Add tests first.
2. Add a per-column deny-list (for example, `customers.phone`) using JSqlParser's `SelectItem` visitor.
3. Make the retry prompt include the list of valid columns when the error is "column not found".
4. Run against `llama3.2:3b` and record, for 20 questions, how many succeed on attempt 1, 2, 3 and 4.
5. Build a second agent (for example, "report-writer") that reads this agent's card and delegates to `/a2a/tasks`.
