effective_date: 2026-06-01
# Runbook: PostgreSQL failover

## When to fail over
Fail over when the primary is unreachable for more than 2 minutes and replication lag on the standby is under 10 seconds.

## Steps
Promote the standby with patronictl failover, then confirm writes succeed from ledger-service.
After failover, open an incident ticket and rebuild the old primary as a new standby.
