effective_date: 2026-03-01
# Data retention policy

## Customer PII
Customer PII is kept for 7 years after account closure for regulatory reasons, then deleted.
PII must be encrypted at rest with AES-256 and masked in logs.

## Application logs
Application logs are retained for 30 days in hot storage and 1 year in cold storage.
