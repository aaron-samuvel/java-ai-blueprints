# Examples

* `src/main/resources/billing-sample.csv` is a runaway `batch-etl` job (800 → 6400 INR).
* Change the last `batch-etl` row back to about 800 and POST it again. The outcome becomes `NO_INCIDENT`.
* `FinOpsIncidentAgentTest` shows the main paths: resolved through scale-down, approval denied leading to escalation, and unknown cause leading to escalation.
