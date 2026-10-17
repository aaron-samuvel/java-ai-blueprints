# Examples

## 1. Discover and call the agent
```bash
mvn -pl blueprint-07-a2a-nl2sql-agent -am spring-boot:run
curl -s localhost:8087/.well-known/agent.json
curl -s -X POST localhost:8087/a2a/tasks -H 'Content-Type: application/json' \
     -d '{"id":"t-1","skill":"nl2sql","message":"How many orders per city?"}'
```
With the offline echo stub, the task ends `FAILED` after 4 attempts, because the stub repeats the
question instead of writing SQL. `artifact.errors` shows each rejection. `A2aEndpointTest` covers
the successful path with a stub that returns valid SQL.

## 2. Use the guard on its own
```java
SqlGuard guard = new SqlGuard(500, Set.of("customers", "orders"));
guard.enforce("SELECT * FROM orders");          // -> "SELECT * FROM orders LIMIT 500"
guard.enforce("DELETE FROM orders");            // -> SqlRejectedException: only SELECT is allowed, got DELETE
```

## 3. Use the agent in code
```java
Nl2SqlAgent agent = new Nl2SqlAgent(chatModel, guard, jdbcTemplate, schemaDdl, 3);
Nl2SqlResult r = agent.answer("Which customers live in Chennai?");
```
