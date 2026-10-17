effective_date: 2026-02-01
# Payments platform architecture

## Service boundaries
The payments platform is split into three services: checkout-api, ledger-service and settlement-worker.
Each service owns its own PostgreSQL schema and no service reads another service's tables.

## Event flow
Checkout-api publishes a PaymentAuthorised event to Kafka; ledger-service consumes it and writes a double-entry record.
Settlement-worker batches ledger entries every 15 minutes and sends them to the acquiring bank.

## Caching
Card-token lookups are cached in Redis for 10 minutes; the cache is never the source of truth for balances.
