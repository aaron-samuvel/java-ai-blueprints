# Examples

## HTTP
```bash
mvn -pl blueprint-04-transactional-swarm -am spring-boot:run
curl -s -X POST localhost:8084/api/conversations/c-1/messages \
  -H 'X-Tenant-Id: acme' -H 'Content-Type: application/json' \
  -d '{"message":"My order ORD-1 arrived broken, refund please"}'
```
With the offline echo stub, triage returns the echoed text (not `REFUND`), so only memory is
written. `SwarmTransactionTest` uses a scripted model to cover the refund path.

## Reuse the tenant memory in any Spring AI app
```java
ChatMemory memory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(new TenantJdbcChatMemoryRepository(jdbcTemplate))
        .maxMessages(20).build();
ChatClient client = ChatClient.builder(model)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build()).build();

TenantContext.callAs("acme", () -> client.prompt().user("hi")
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c-1")).call().content());
```
