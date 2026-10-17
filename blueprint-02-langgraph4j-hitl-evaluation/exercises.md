# Exercises: Blueprint 02

*(author's design, not from the handbook)*

1. Add a `fact_checker` node that runs in parallel with `evaluator` and must also pass.
2. Replace `MemorySaver` with a file- or JDBC-based checkpoint saver, and show a review surviving an app restart.
3. Add a time-out: reviews waiting more than 48 hours are auto-rejected by a scheduled job.
4. Stream node outputs (`graph.stream(...)`) to the browser with Server-Sent Events.
5. Use Ollama for the drafter and count how many drafts pass the evaluator on revision 0, 1 and 2.
