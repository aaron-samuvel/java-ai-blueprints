# Examples

## 1. HTTP walkthrough (stub model, offline)
```bash
mvn -pl blueprint-05-pii-tokenization-advisor -am spring-boot:run
curl -s -X POST localhost:8085/api/chat -H 'Content-Type: application/json' \
  -d '{"message":"My PAN is ABCDE1234F and phone +91 98765 43210"}'
# {"answer":"echo: My PAN is ABCDE1234F and phone +91 98765 43210", ...}
# The echo stub received:  "My PAN is <<PAN_1>> and phone <<PHONE_1>>"
```
(The response shape is shown for illustration. The test `PiiAdvisorApplicationTest` asserts the same behaviour.)

## 2. Use the advisor in your own app
```java
ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(new PiiTokenizationAdvisor(new PiiTokenizer()))
        .build();
```

## 3. Annotate a tool
```java
@Tool(description = "Customer contact profile")
@ToolPrivacyPolicy(value = ToolPrivacyPolicy.Mode.TOKENIZE, reason = "contact details")
public String customerProfile(String id) { ... }

List<ToolCallback> safeTools = PrivacyEnforcingToolCallback.wrap(tokenizer, vault, customerTools);
```
