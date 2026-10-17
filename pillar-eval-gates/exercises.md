# Exercises: evaluation gates

*(author's design, not from the handbook)*

1. Add 5 golden cases built from Blueprint 03's corpus. Generate the answers with `ModularRagService` instead of writing them by hand.
2. Write a paraphrased but correct answer that the lexical judge wrongly fails. Check whether the Ollama judge (`-Pit`) gets it right.
3. Add a third evaluator: answer length or a "refuses when context is empty" check.
4. Run the live gate 5 times and measure how much the pass rate varies. Pick a threshold from the data.
5. Publish the `GateReport` as a JUnit report attachment or a Markdown file in `target/`.
