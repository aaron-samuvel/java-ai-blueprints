# Theory: transactional swarms and tenant-partitioned memory

The handbook names "Multi-agent transactional swarm with tenant-partitioned ChatMemory" and the
pillar "unified data and transactions". The details are **author's design (not from handbook)**.

## The problem
Multi-agent flows mutate several things: conversation memory, business rows, and messages to
the outside world. If agent 3 fails after agent 2 wrote a refund, you get a refund nobody was told
about, and a memory that "remembers" an action that didn't happen. The next turn then reasons from
false history.

## One database, one transaction
Keep **chat memory in the same relational database** as the business data, and run the swarm in
one `@Transactional` method. Spring binds a single JDBC connection to the thread. Every
`JdbcTemplate` call, including the ones Spring AI's `MessageChatMemoryAdvisor` makes through our
`ChatMemoryRepository`, joins it. Any runtime exception rolls everything back. The tests prove it
by checking the row counts after a failure.

## Outbox instead of sending directly
The notify agent doesn't send email; it **inserts an outbox row** in the same transaction. A
separate relay publishes outbox rows after commit. A rollback therefore removes the notification
too, so there's no "sent but rolled back".

## Tenant partitioning
- `tenant_id` is part of the primary key and of **every** `WHERE` clause.
- The repository takes the tenant from `TenantContext`, never from its arguments, so a caller
  can't ask for another tenant's conversation by passing its id.
- No tenant gives `IllegalStateException`: fail closed.
- Next steps for production: Postgres row-level security (`USING (tenant_id = current_setting('app.tenant'))`)
  as a second line of defence.
