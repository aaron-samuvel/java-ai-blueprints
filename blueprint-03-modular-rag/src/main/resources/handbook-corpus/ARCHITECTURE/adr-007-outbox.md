effective_date: 2026-04-15
# ADR-007: Transactional outbox

## Decision
Services publish domain events through a transactional outbox table written in the same database transaction as the business change.
A relay process reads the outbox and publishes to Kafka with at-least-once delivery, so consumers must be idempotent.

## Consequences
Dual writes to the database and Kafka are forbidden; any design that needs one must use the outbox pattern instead.
