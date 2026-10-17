effective_date: 2026-05-20
# Runbook: Kafka consumer lag

## Alert
The alert fires when consumer lag on the ledger topic exceeds 10000 messages for 5 minutes.

## Recovery steps
First check the consumer group with kafka-consumer-groups --describe and look for a stuck partition.
If one consumer is stuck, restart that pod; if lag is spread evenly, scale ledger-service to 6 replicas.
Never reset offsets in production without approval from the incident commander.
